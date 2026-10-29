package com.aptech.aptech4.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Shared dynamic properties for integration tests (JWT secrets, CORS, mock payment).
 * Datasource/Redis wiring is either Compose {@code IT_*} env or Testcontainers — see ADR-009.
 */
public final class IntegrationTestSupport {

	private IntegrationTestSupport() {
	}

	public static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	/**
	 * Registers JWT/CORS (and optional mock-payment) properties required for full context boot.
	 */
	public static void registerSecurityProperties(DynamicPropertyRegistry registry) {
		registry.add("aptech.security.jwt.access-secret", () -> "access-secret-value-32chars-minimum!!");
		registry.add("aptech.security.jwt.refresh-secret", () -> "refresh-secret-value-32chars-minimum!");
		registry.add("aptech.security.jwt.issuer", () -> "aptech4-api");
		registry.add("aptech.security.jwt.access-ttl-seconds", () -> "900");
		// Include Compose web origin (:8088) — browsers send Origin on JSON POSTs even via same-host nginx.
		registry.add(
				"aptech.security.cors.allowed-origins",
				() -> "http://localhost:5173,http://localhost:8088,http://127.0.0.1:8088"
		);
	}

	public static void registerMockPaymentTestProperties(DynamicPropertyRegistry registry) {
		registry.add("aptech.payment.mock.allow-client-force-failure", () -> "true");
		registry.add("aptech.payment.mock.default-outcome", () -> "SUCCEEDED");
	}

	/**
	 * Compose Postgres/Redis via {@code IT_*} (defaults assume published localhost ports).
	 */
	public static void registerComposeDataSources(DynamicPropertyRegistry registry, String defaultDb) {
		String pgHost = env("IT_POSTGRES_HOST", "localhost");
		String pgPort = env("IT_POSTGRES_PORT", "5432");
		String pgDb = env("IT_POSTGRES_DB", defaultDb);
		String pgUser = env("IT_POSTGRES_USER", "aptech");
		String pgPassword = env("IT_POSTGRES_PASSWORD", "aptech_dev_change_me");
		String redisHost = env("IT_REDIS_HOST", "localhost");
		String redisPort = env("IT_REDIS_PORT", "6379");

		registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb);
		registry.add("spring.datasource.username", () -> pgUser);
		registry.add("spring.datasource.password", () -> pgPassword);
		registry.add("spring.data.redis.host", () -> redisHost);
		registry.add("spring.data.redis.port", () -> redisPort);
		// Keep IT pools small — many SpringBootTest contexts share one local Postgres (max_connections≈100).
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
	}
}
