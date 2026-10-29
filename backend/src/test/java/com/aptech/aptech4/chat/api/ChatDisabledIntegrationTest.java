package com.aptech.aptech4.chat.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
class ChatDisabledIntegrationTest {

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
	void healthShowsDisabledAndMessagesReturn503() throws Exception {
		String token = customerToken();

		mockMvc.perform(get("/api/v1/chat/health")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.status").value("DISABLED"));

		mockMvc.perform(post("/api/v1/chat/messages")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "message": "What is my latest order?" }
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.error").value("AI Disabled"));
	}

	@Test
	void unauthenticatedChatIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/chat/health"))
				.andExpect(status().isUnauthorized());
	}

	private String customerToken() {
		return transactionTemplate.execute(status -> {
			String email = "chat.cust." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
			Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
			UserAccount account = UserAccount.createNew(email, passwordEncoder.encode("Password1!"), "Chat Cust");
			account.setRoles(Set.of(customer));
			UserAccount saved = userAccountRepository.saveAndFlush(account);
			return jwtService.createAccessToken(saved.getId(), email, List.of("CUSTOMER"));
		});
	}
}
