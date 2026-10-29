package com.aptech.aptech4.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.aptech.aptech4.config.AdminPathProbeController;
import com.aptech.aptech4.config.SecurityProbeController;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RefreshTokenHasher;
import com.aptech.aptech4.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * P07-T02 security regression gaps (JWT filter edge cases, reuse family revoke, CORS credentials,
 * error hygiene). Broader auth/RBAC coverage lives in {@link AuthIntegrationTest},
 * {@code JwtSecurityWebTest}, and {@code RbacSecurityWebTest}.
 *
 * <p>Runs against ephemeral Postgres 16 + Redis 7 (Testcontainers / ADR-009).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ SecurityProbeController.class, AdminPathProbeController.class })
@Testcontainers(disabledWithoutDocker = true)
class AuthSecurityRegressionTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
	private static final String ACCESS_SECRET = "access-secret-value-32chars-minimum!!";
	private static final String REFRESH_SECRET = "refresh-secret-value-32chars-minimum!";

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_security_it")
			.withUsername("aptech")
			.withPassword("aptech_tc");

	@Container
	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
		IntegrationTestSupport.registerSecurityProperties(registry);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void bearerEdgeCasesAndForgedClaimsReturn401WithSafeBody() throws Exception {
		UUID userId = UUID.randomUUID();
		SecretKey accessKey = Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8));

		String wrongIssuer = Jwts.builder()
				.issuer("evil-issuer")
				.subject(userId.toString())
				.expiration(Date.from(Instant.now().plusSeconds(120)))
				.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
				.claim(JwtService.CLAIM_EMAIL, "x@example.com")
				.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
				.signWith(accessKey)
				.compact();

		String wrongUse = Jwts.builder()
				.issuer("aptech4-api")
				.subject(userId.toString())
				.expiration(Date.from(Instant.now().plusSeconds(120)))
				.claim(JwtService.CLAIM_TOKEN_USE, "session")
				.claim(JwtService.CLAIM_EMAIL, "x@example.com")
				.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
				.signWith(accessKey)
				.compact();

		String expired = Jwts.builder()
				.issuer("aptech4-api")
				.subject(userId.toString())
				.expiration(Date.from(Instant.now().minusSeconds(30)))
				.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
				.claim(JwtService.CLAIM_EMAIL, "x@example.com")
				.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
				.signWith(accessKey)
				.compact();

		assertUnauthorizedSafe(get("/api/v1/__probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongIssuer));
		assertUnauthorizedSafe(get("/api/v1/__probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongUse));
		assertUnauthorizedSafe(get("/api/v1/__probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));
		assertUnauthorizedSafe(get("/api/v1/__probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer "));
		assertUnauthorizedSafe(get("/api/v1/__probe/me").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));
		assertUnauthorizedSafe(get("/api/v1/__probe/me"));
	}

	@Test
	void unknownEmailAndWrongPasswordShareGenericMessage() throws Exception {
		String email = "exists." + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password1!","fullName":"Exists"}
								""".formatted(email)))
				.andExpect(status().isCreated());

		MvcResult unknown = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"missing.%s@example.com","password":"Password1!"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"))
				.andReturn();

		MvcResult wrong = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"WrongPassword1!"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"))
				.andReturn();

		assertSafeAuthError(unknown.getResponse().getContentAsString());
		assertSafeAuthError(wrong.getResponse().getContentAsString());
		assertThat(unknown.getResponse().getContentAsString())
				.doesNotContain("UserDetails")
				.doesNotContain("not found");
	}

	@Test
	void refreshReuseRevokesSiblingSessionsAndFailureHasNoAccessToken() throws Exception {
		String email = "reuse." + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password1!","fullName":"Reuse"}
								""".formatted(email)))
				.andExpect(status().isCreated());

		JsonNode sessionA = login(email, "Password1!");
		JsonNode sessionB = login(email, "Password1!");
		String refreshA = sessionA.get("refreshToken").asText();
		String refreshB = sessionB.get("refreshToken").asText();
		assertThat(refreshA).isNotEqualTo(refreshB);

		MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshA)))
				.andExpect(status().isOk())
				.andReturn();
		String refreshA2 = objectMapper.readTree(rotated.getResponse().getContentAsString())
				.get("refreshToken")
				.asText();

		MvcResult reuse = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshA)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid refresh token"))
				.andExpect(jsonPath("$.accessToken").doesNotExist())
				.andExpect(jsonPath("$.refreshToken").doesNotExist())
				.andReturn();
		String reuseBody = reuse.getResponse().getContentAsString();
		assertThat(reuseBody).doesNotContain(refreshA).doesNotContain(refreshA2).doesNotContain(refreshB);
		assertSafeAuthError(reuseBody);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshB)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.accessToken").doesNotExist());

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refreshA2)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.accessToken").doesNotExist());

		UUID userId = UUID.fromString(sessionA.path("user").path("id").asText());
		Integer active = jdbcTemplate.queryForObject(
				"select count(*) from refresh_tokens where user_id = ? and revoked_at is null",
				Integer.class,
				userId);
		assertThat(active).isZero();

		String stored = jdbcTemplate.queryForObject(
				"select token_hash from refresh_tokens where user_id = ? order by created_at desc limit 1",
				String.class,
				userId);
		assertThat(stored).hasSize(64).isEqualTo(RefreshTokenHasher.sha256Hex(refreshA2));
		assertThat(stored).doesNotContain(refreshA2);
	}

	@Test
	void corsAllowsCredentialsForConfiguredOriginOnly() throws Exception {
		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,Authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

		mockMvc.perform(options("/api/v1/auth/login")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void registerFromComposeWebOriginSucceedsAndEvilOriginIsBlocked() throws Exception {
		String email = "stack-cors-" + UUID.randomUUID() + "@example.com";
		String body = """
				{"email":"%s","password":"ValidPass1!","fullName":"Stack CORS User"}
				""".formatted(email);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.header(HttpHeaders.ORIGIN, "http://localhost:8088")
						.content(body))
				.andExpect(status().isCreated())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8088"))
				.andExpect(jsonPath("$.user.email").value(email))
				.andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(jsonPath("$.password").doesNotExist());

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.content("""
								{"email":"evil-%s@example.com","password":"ValidPass1!","fullName":"Evil"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isForbidden());
	}

	@Test
	void forbiddenAndUnauthorizedBodiesHaveNoStackOrSecrets() throws Exception {
		JsonNode session = register("hygiene." + UUID.randomUUID() + "@example.com");
		String access = session.get("accessToken").asText();

		MvcResult forbidden = mockMvc.perform(get("/api/v1/admin/__probe")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andReturn();
		assertSafeAuthError(forbidden.getResponse().getContentAsString());
		assertThat(forbidden.getResponse().getContentAsString()).doesNotContain(access);

		MvcResult unauthorized = mockMvc.perform(get("/api/v1/admin/__probe"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andReturn();
		assertSafeAuthError(unauthorized.getResponse().getContentAsString());
	}

	private JsonNode register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"Password1!","fullName":"Sec User"}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private JsonNode login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void assertUnauthorizedSafe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		MvcResult result = mockMvc.perform(request)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andReturn();
		assertSafeAuthError(result.getResponse().getContentAsString());
	}

	private static void assertSafeAuthError(String body) {
		assertThat(body)
				.doesNotContain("Password1!")
				.doesNotContain(ACCESS_SECRET)
				.doesNotContain(REFRESH_SECRET)
				.doesNotContain("stackTrace")
				.doesNotContain("java.lang.")
				.doesNotContain("Caused by:");
	}
}
