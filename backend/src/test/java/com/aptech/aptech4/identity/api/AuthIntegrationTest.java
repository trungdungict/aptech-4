package com.aptech.aptech4.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.aptech.aptech4.config.AdminPathProbeController;
import com.aptech.aptech4.config.SecurityProbeController;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RefreshTokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Auth IT against Compose Postgres/Redis (Docker-in-Docker Testcontainers is unreliable on Docker Desktop).
 * Set {@code IT_POSTGRES_HOST}/{@code IT_REDIS_HOST} when the Maven runner is on the Compose network.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ SecurityProbeController.class, AdminPathProbeController.class })
class AuthIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		String pgHost = env("IT_POSTGRES_HOST", "localhost");
		String pgPort = env("IT_POSTGRES_PORT", "5432");
		String pgDb = env("IT_POSTGRES_DB", "aptech4_auth_it");
		String pgUser = env("IT_POSTGRES_USER", "aptech");
		String pgPassword = env("IT_POSTGRES_PASSWORD", "aptech_dev_change_me");
		String redisHost = env("IT_REDIS_HOST", "localhost");
		String redisPort = env("IT_REDIS_PORT", "6379");

		registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb);
		registry.add("spring.datasource.username", () -> pgUser);
		registry.add("spring.datasource.password", () -> pgPassword);
		registry.add("spring.data.redis.host", () -> redisHost);
		registry.add("spring.data.redis.port", () -> redisPort);
		registry.add("aptech.security.jwt.access-secret", () -> "access-secret-value-32chars-minimum!!");
		registry.add("aptech.security.jwt.refresh-secret", () -> "refresh-secret-value-32chars-minimum!");
		registry.add("aptech.security.jwt.issuer", () -> "aptech4-api");
		registry.add("aptech.security.jwt.access-ttl-seconds", () -> "900");
		registry.add("aptech.security.cors.allowed-origins", () -> "http://localhost:5173");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	MockMvc mockMvc;

	/** Boot 4 uses Jackson 3 JsonMapper beans; keep a local Jackson 2 mapper for JWT/test JSON parsing. */
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	JwtService jwtService;

	@Test
	void registerCreatesCustomerAndReturnsTokens() throws Exception {
		String emailLocal = "new.user." + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "New User"
						}
						""".formatted(mixedCase(emailLocal))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user.email").value(emailLocal))
			.andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
			.andExpect(jsonPath("$.user.password").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		String access = body.get("accessToken").asText();
		String refresh = body.get("refreshToken").asText();
		assertThat(access).isNotEqualTo(refresh);

		Claims claims = parseAccessClaims(access);
		assertThat(claims.getIssuer()).isEqualTo("aptech4-api");
		assertThat(claims.get(JwtService.CLAIM_TOKEN_USE, String.class)).isEqualTo(JwtService.TOKEN_USE_ACCESS);

		UUID userId = UUID.fromString(body.path("user").path("id").asText());
		String storedHash = jdbcTemplate.queryForObject(
				"select token_hash from refresh_tokens where user_id = ?",
				String.class,
				userId
		);
		assertThat(storedHash).isEqualTo(RefreshTokenHasher.sha256Hex(refresh));
		assertThat(storedHash).doesNotContain(refresh);
		Integer plaintextHits = jdbcTemplate.queryForObject(
				"select count(*) from refresh_tokens where token_hash = ?",
				Integer.class,
				refresh
		);
		assertThat(plaintextHits).isZero();

		String passwordHash = jdbcTemplate.queryForObject(
				"select password_hash from users where email = ?",
				String.class,
				emailLocal
		);
		assertThat(passwordHash).startsWith("$2").doesNotContain("Password1!");

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isOk());
	}

	@Test
	void duplicateEmailReturnsConflict() throws Exception {
		String email = "dup." + UUID.randomUUID() + "@example.com";
		String payload = """
				{
				  "email": "%s",
				  "password": "Password1!",
				  "fullName": "Dup User"
				}
				""".formatted(email);
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Email already registered"));
	}

	@Test
	void invalidRegisterRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "not-an-email",
						  "password": "short",
						  "fullName": ""
						}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error").value("Bad Request"));
	}

	@Test
	void loginReturnsTokensAndAuthorizesProbe() throws Exception {
		String email = "login.user." + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "Login User"
						}
						""".formatted(email)))
			.andExpect(status().isCreated());

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!"
						}
						""".formatted(mixedCase(email))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
			.andReturn();

		String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden());
	}

	@Test
	void loginWithWrongPasswordReturnsGenericUnauthorized() throws Exception {
		String email = "wrong.pass." + UUID.randomUUID() + "@example.com";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "Wrong Pass"
						}
						""".formatted(email)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "BadPassword1!"
						}
						""".formatted(email)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void refreshRotatesAndRejectsOldToken() throws Exception {
		JsonNode session = registerSession("refresh.ok." + UUID.randomUUID() + "@example.com");
		String oldRefresh = session.get("refreshToken").asText();

		MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(oldRefresh)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty())
			.andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
			.andReturn();

		JsonNode body = objectMapper.readTree(rotated.getResponse().getContentAsString());
		String newRefresh = body.get("refreshToken").asText();
		assertThat(newRefresh).isNotEqualTo(oldRefresh);

		String access = body.get("accessToken").asText();
		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isOk());

		MvcResult reuse = mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(oldRefresh)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"))
			.andReturn();
		assertThat(reuse.getResponse().getContentAsString()).doesNotContain(oldRefresh);

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(newRefresh)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void refreshRejectsAccessTokenWrongSignatureAndMissing() throws Exception {
		JsonNode session = registerSession("refresh.bad." + UUID.randomUUID() + "@example.com");
		String access = session.get("accessToken").asText();

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(access)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "not-a-jwt" }
						"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));

		UUID userId = UUID.fromString(session.path("user").path("id").asText());
		String orphanRefresh = jwtService.createRefreshToken(userId);
		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(orphanRefresh)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void refreshTokenCannotBeUsedAsBearerAccess() throws Exception {
		JsonNode session = registerSession("bearer.refresh." + UUID.randomUUID() + "@example.com");
		String refresh = session.get("refreshToken").asText();

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		JsonNode session = registerSession("logout." + UUID.randomUUID() + "@example.com");
		String refresh = session.get("refreshToken").asText();

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(refresh)))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(refresh)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "refreshToken": "%s" }
						""".formatted(refresh)))
			.andExpect(status().isNoContent());
	}

	@Test
	void concurrentRefreshAllowsOnlyOneSuccess() throws Exception {
		JsonNode session = registerSession("race.refresh." + UUID.randomUUID() + "@example.com");
		String refresh = session.get("refreshToken").asText();
		String payload = """
				{ "refreshToken": "%s" }
				""".formatted(refresh);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> task = () -> mockMvc.perform(post("/api/v1/auth/refresh")
					.contentType(MediaType.APPLICATION_JSON)
					.content(payload))
				.andReturn()
				.getResponse()
				.getStatus();
			Future<Integer> a = pool.submit(task);
			Future<Integer> b = pool.submit(task);
			assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 401);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void tamperedAccessTokenRejected() throws Exception {
		MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "token.check.%s@example.com",
						  "password": "Password1!",
						  "fullName": "Token Check"
						}
						""".formatted(UUID.randomUUID())))
			.andExpect(status().isCreated())
			.andReturn();

		UUID userId = UUID.fromString(objectMapper.readTree(register.getResponse().getContentAsString())
			.path("user").path("id").asText());

		SecretKey wrongKey = Keys.hmacShaKeyFor("wrong-secret-value-32chars-minimum!!!".getBytes(StandardCharsets.UTF_8));
		String badSig = Jwts.builder()
			.issuer("aptech4-api")
			.subject(userId.toString())
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
			.signWith(wrongKey)
			.compact();

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + badSig))
			.andExpect(status().isUnauthorized());

		SecretKey accessKey = Keys.hmacShaKeyFor("access-secret-value-32chars-minimum!!".getBytes(StandardCharsets.UTF_8));
		String expired = Jwts.builder()
			.issuer("aptech4-api")
			.subject(userId.toString())
			.expiration(Date.from(Instant.now().minusSeconds(30)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(accessKey)
			.compact();

		mockMvc.perform(get("/api/v1/__probe/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentDuplicateRegisterHonorsUniqueConstraint() throws Exception {
		String payload = """
				{
				  "email": "race.%s@example.com",
				  "password": "Password1!",
				  "fullName": "Race User"
				}
				""".formatted(UUID.randomUUID());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> task = () -> mockMvc.perform(post("/api/v1/auth/register")
					.contentType(MediaType.APPLICATION_JSON)
					.content(payload))
				.andReturn()
				.getResponse()
				.getStatus();
			Future<Integer> a = pool.submit(task);
			Future<Integer> b = pool.submit(task);
			assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void registerIgnoresClientSuppliedAdminRole() throws Exception {
		String email = "elevate." + UUID.randomUUID() + "@example.com";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "No Elevate",
						  "role": "ADMIN",
						  "roles": ["ADMIN"]
						}
						""".formatted(email)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.user.roles.length()").value(1))
			.andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
			.andReturn();

		String access = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
		mockMvc.perform(get("/api/v1/admin/__probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/__probe/customer")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isOk());
	}

	@Test
	void customerTokenForbiddenOnAdminPathAndAnonymousUnauthorized() throws Exception {
		JsonNode session = registerSession("rbac.cust." + UUID.randomUUID() + "@example.com");
		String access = session.get("accessToken").asText();

		mockMvc.perform(get("/api/v1/admin/__probe"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/__probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isForbidden());
	}

	@Test
	void openApiStillPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/auth/register']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/auth/logout']").exists());
	}

	private JsonNode registerSession(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "Session User"
						}
						""".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static String mixedCase(String email) {
		String[] parts = email.split("@", 2);
		return parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1) + "@" + parts[1].toUpperCase();
	}

	private static Claims parseAccessClaims(String token) {
		SecretKey accessKey = Keys.hmacShaKeyFor("access-secret-value-32chars-minimum!!".getBytes(StandardCharsets.UTF_8));
		return Jwts.parser()
			.verifyWith(accessKey)
			.requireIssuer("aptech4-api")
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
