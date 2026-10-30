package com.aptech.aptech4.chat.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.support.IntegrationTestSupport;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Chat endpoint JWT gate: missing and expired tokens must be 401 (never LLM apologies).
 * AI is disabled so failures stay at the security filter — identity is never taken from prompts.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatAuthIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		IntegrationTestSupport.registerComposeDataSources(registry, "aptech4_catalog_it");
		IntegrationTestSupport.registerSecurityProperties(registry);
		IntegrationTestSupport.registerMockPaymentTestProperties(registry);
		registry.add("aptech.ai.enabled", () -> "false");
		registry.add("spring.ai.model.chat", () -> "none");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void missingJwtIsUnauthorizedForRestAndStream() throws Exception {
		mockMvc.perform(post("/api/v1/chat/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "message": "How many orders have I placed?" }
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("Unauthorized"));

		mockMvc.perform(post("/api/v1/chat/messages/stream")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "message": "How many orders have I placed?" }
								"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredJwtIsUnauthorized() throws Exception {
		String expired = Jwts.builder()
				.issuer("aptech4-api")
				.subject(UUID.randomUUID().toString())
				.expiration(Date.from(Instant.now().minusSeconds(30)))
				.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
				.claim(JwtService.CLAIM_EMAIL, "expired@example.com")
				.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
				.signWith(Keys.hmacShaKeyFor(
						"access-secret-value-32chars-minimum!!".getBytes(StandardCharsets.UTF_8)))
				.compact();

		mockMvc.perform(post("/api/v1/chat/messages")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "message": "How many orders have I placed?" }
								"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/chat/health")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void validCustomerCanReachHealthWhenAiDisabled() throws Exception {
		String token = customerToken();
		mockMvc.perform(get("/api/v1/chat/health")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));
	}

	private String customerToken() {
		return transactionTemplate.execute(status -> {
			String email = "chat.auth." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
			Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
			UserAccount account = UserAccount.createNew(email, passwordEncoder.encode("Password1!"), "Chat Auth");
			account.setRoles(Set.of(customer));
			UserAccount saved = userAccountRepository.saveAndFlush(account);
			return jwtService.createAccessToken(saved.getId(), email, List.of("CUSTOMER"));
		});
	}
}
