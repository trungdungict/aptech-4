package com.aptech.aptech4.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Canonical Testcontainers sample (ADR-009): ephemeral Postgres + Redis, full Spring context.
 * Skipped automatically when Docker is unavailable ({@code disabledWithoutDocker}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersBootstrapTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_tc")
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

	@Autowired
	StringRedisTemplate stringRedisTemplate;

	@Test
	void contextLoadsAgainstEphemeralPostgresAndRedis() throws Exception {
		Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		assertThat(one).isEqualTo(1);

		Integer changesetCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM databasechangelog",
				Integer.class);
		assertThat(changesetCount).isNotNull().isPositive();

		String key = "tc-bootstrap:" + System.nanoTime();
		stringRedisTemplate.opsForValue().set(key, "ok");
		assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("ok");
		stringRedisTemplate.delete(key);

		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OK"))
				.andExpect(jsonPath("$.service").value("aptech4-api"));
	}
}
