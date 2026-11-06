package com.aptech.aptech4.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.aptech.aptech4.support.IntegrationTestSupport;

/**
 * Optional smoke against Compose Postgres ({@code RUN_COMPOSE_MIGRATE_SMOKE=true}).
 * Applies pending Liquibase changesets (including 145) without volume wipe.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_COMPOSE_MIGRATE_SMOKE", matches = "true")
class ComposeTranslationMigrateSmokeTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		IntegrationTestSupport.registerComposeDataSources(registry, "aptech4");
		IntegrationTestSupport.registerSecurityProperties(registry);
	}

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void translationChangesetsPresentOnComposeDatabase() {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '145-%'",
				Integer.class);
		assertThat(count).isEqualTo(7);

		Integer productEn = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_translations WHERE locale = 'en'",
				Integer.class);
		Integer categoryEn = jdbc.queryForObject(
				"SELECT COUNT(*) FROM category_translations WHERE locale = 'en'",
				Integer.class);
		Integer strategyVi = jdbc.queryForObject(
				"SELECT COUNT(*) FROM promotion_strategy_translations WHERE locale = 'vi'",
				Integer.class);

		// Demo seed may or may not have run; tables must exist and counts must be non-negative.
		assertThat(productEn).isNotNull().isGreaterThanOrEqualTo(0);
		assertThat(categoryEn).isNotNull().isGreaterThanOrEqualTo(0);
		assertThat(strategyVi).isNotNull().isGreaterThanOrEqualTo(0);
	}
}
