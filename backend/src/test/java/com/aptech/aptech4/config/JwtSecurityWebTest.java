package com.aptech.aptech4.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.aptech.aptech4.api.HealthController;
import com.aptech.aptech4.identity.security.JwtAuthenticationFilter;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RestAccessDeniedHandler;
import com.aptech.aptech4.identity.security.RestAuthenticationEntryPoint;

@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest(controllers = {
		HealthController.class,
		SecurityProbeController.class,
		AdminPathProbeController.class
})
@Import({
		SecurityConfig.class,
		JwtService.class,
		JwtAuthenticationFilter.class,
		RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestPropertySource(properties = {
		"aptech.security.jwt.access-secret=access-secret-value-32chars-minimum!!",
		"aptech.security.jwt.refresh-secret=refresh-secret-value-32chars-minimum!",
		"aptech.security.jwt.issuer=aptech4-api",
		"aptech.security.jwt.access-ttl-seconds=900",
		"aptech.security.jwt.refresh-ttl-seconds=3600",
		"aptech.security.cors.allowed-origins=http://localhost:5173",
		"aptech.security.cors.allow-credentials=true",
		"springdoc.api-docs.enabled=true",
		"springdoc.swagger-ui.enabled=true"
})
class JwtSecurityWebTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Test
	void healthRemainsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OK"));
	}

	@Test
	void protectedProbeReturns401WithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/__probe/admin"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void invalidSignatureReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid.jwt.token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.error").value("Unauthorized"));
	}

	@Test
	void wrongSignatureForgedAdminRolesReturns401() throws Exception {
		String forged = io.jsonwebtoken.Jwts.builder()
			.issuer("aptech4-api")
			.subject(UUID.randomUUID().toString())
			.expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(120)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "evil@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
			.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
					"different-secret-32chars-minimum!!!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
			.compact();

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongIssuerReturns401() throws Exception {
		String token = io.jsonwebtoken.Jwts.builder()
			.issuer("not-aptech4")
			.subject(UUID.randomUUID().toString())
			.expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(120)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
			.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
					"access-secret-value-32chars-minimum!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
			.compact();

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongTokenUseReturns401() throws Exception {
		String token = io.jsonwebtoken.Jwts.builder()
			.issuer("aptech4-api")
			.subject(UUID.randomUUID().toString())
			.expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(120)))
			.claim(JwtService.CLAIM_TOKEN_USE, "not-access")
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
			.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
					"access-secret-value-32chars-minimum!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
			.compact();

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredAccessTokenReturns401() throws Exception {
		String expired = io.jsonwebtoken.Jwts.builder()
			.issuer("aptech4-api")
			.subject(UUID.randomUUID().toString())
			.expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(5)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
					"access-secret-value-32chars-minimum!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
			.compact();

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshTokenCannotCallApi() throws Exception {
		String refresh = jwtService.createRefreshToken(UUID.randomUUID());
		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void customerReceives403OnAdminProbe() throws Exception {
		String access = jwtService.createAccessToken(
				UUID.randomUUID(), "cust@example.com", List.of("CUSTOMER"));

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void adminReceives200OnAdminProbe() throws Exception {
		String access = jwtService.createAccessToken(
				UUID.randomUUID(), "admin@example.com", List.of("ADMIN"));

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void corsAllowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/health")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void corsRejectsUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/health")
				.header(HttpHeaders.ORIGIN, "http://evil.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}
}
