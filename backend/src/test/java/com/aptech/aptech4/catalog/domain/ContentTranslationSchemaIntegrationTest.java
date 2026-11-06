package com.aptech.aptech4.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.aptech.aptech4.support.IntegrationTestSupport;

/**
 * Verifies Liquibase 145 translation tables, constraints, indexes, and backfill SQL shape
 * against an ephemeral Postgres (no volume wipe of the local demo DB).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ContentTranslationSchemaIntegrationTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_i18n_tc")
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
	JdbcTemplate jdbc;

	@Test
	void liquibaseAppliedTranslationChangesets() {
		List<String> ids = jdbc.queryForList(
				"SELECT id FROM databasechangelog WHERE id LIKE '145-%' ORDER BY id",
				String.class);
		assertThat(ids).containsExactly(
				"145-backfill-catalog-en-translations",
				"145-backfill-promotion-vi-translations",
				"145-category-translations",
				"145-product-translations",
				"145-promotion-campaign-translations",
				"145-promotion-placement-translations",
				"145-promotion-strategy-translations"
		);
	}

	@Test
	void translationTablesExistWithExpectedColumns() {
		assertThat(tableExists("product_translations")).isTrue();
		assertThat(tableExists("category_translations")).isTrue();
		assertThat(tableExists("promotion_strategy_translations")).isTrue();
		assertThat(tableExists("promotion_campaign_translations")).isTrue();
		assertThat(tableExists("promotion_placement_translations")).isTrue();

		assertThat(columns("product_translations"))
				.contains("id", "product_id", "locale", "name", "description");
		assertThat(columns("category_translations"))
				.contains("id", "category_id", "locale", "name", "description");
		assertThat(columns("promotion_strategy_translations"))
				.contains("id", "strategy_id", "locale", "name");
		assertThat(columns("promotion_campaign_translations"))
				.contains("id", "campaign_id", "locale", "name");
		assertThat(columns("promotion_placement_translations"))
				.contains("id", "placement_id", "locale", "title", "subtitle", "cta_label");
	}

	@Test
	void localeCheckUniqueAndCascadeConstraintsHold() {
		UUID categoryId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();

		jdbc.update(
				"""
				INSERT INTO categories (id, name, slug, description, active, created_at, updated_at)
				VALUES (?, 'Fruit', 'tc-fruit', 'desc', TRUE, NOW(), NOW())
				""",
				categoryId);
		jdbc.update(
				"""
				INSERT INTO products (
				  id, category_id, sku, name, description, unit_price, currency, active, created_at, updated_at
				) VALUES (?, ?, 'TC-SKU-001', 'Apples', 'Fresh', ?, 'VND', TRUE, NOW(), NOW())
				""",
				productId, categoryId, new BigDecimal("10000.00"));

		jdbc.update(
				"""
				INSERT INTO product_translations (id, product_id, locale, name, description)
				VALUES (?, ?, 'en', 'Apples', 'Fresh')
				""",
				UUID.randomUUID(), productId);

		assertThatThrownBy(() -> jdbc.update(
				"""
				INSERT INTO product_translations (id, product_id, locale, name, description)
				VALUES (?, ?, 'en', 'Dup', NULL)
				""",
				UUID.randomUUID(), productId))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> jdbc.update(
				"""
				INSERT INTO product_translations (id, product_id, locale, name, description)
				VALUES (?, ?, 'fr', 'Pommes', NULL)
				""",
				UUID.randomUUID(), productId))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbc.update("DELETE FROM products WHERE id = ?", productId);
		Integer remaining = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_translations WHERE product_id = ?",
				Integer.class,
				productId);
		assertThat(remaining).isZero();
	}

	@Test
	void backfillSqlCopiesCatalogEnAndPromotionViIdempotently() {
		UUID categoryId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID strategyId = UUID.randomUUID();
		UUID campaignId = UUID.randomUUID();
		UUID placementId = UUID.randomUUID();

		jdbc.update(
				"""
				INSERT INTO categories (id, name, slug, description, active, created_at, updated_at)
				VALUES (?, 'Beverages', 'tc-bev', 'Drinks', TRUE, NOW(), NOW())
				""",
				categoryId);
		jdbc.update(
				"""
				INSERT INTO products (
				  id, category_id, sku, name, description, unit_price, currency, active, created_at, updated_at
				) VALUES (?, ?, 'TC-SKU-BEV', 'Juice', 'Orange juice', ?, 'VND', TRUE, NOW(), NOW())
				""",
				productId, categoryId, new BigDecimal("20000.00"));
		jdbc.update(
				"""
				INSERT INTO promotion_strategies (
				  id, name, code, type, discount_percent, stackable, priority, active, created_at, updated_at
				) VALUES (?, 'Ưu đãi %', 'TC-PROMO', 'PERCENTAGE_DISCOUNT', 10, FALSE, 100, TRUE, NOW(), NOW())
				""",
				strategyId);
		jdbc.update(
				"""
				INSERT INTO promotion_campaigns (
				  id, strategy_id, name, slug, status, starts_at, ends_at, priority, stackable, notes, created_at, updated_at
				) VALUES (
				  ?, ?, 'Chiến dịch demo', 'tc-campaign', 'PAUSED',
				  NOW() - INTERVAL '1 day', NOW() + INTERVAL '30 day', 100, FALSE, 'ops note', NOW(), NOW()
				)
				""",
				campaignId, strategyId);
		jdbc.update(
				"""
				INSERT INTO promotion_placements (
				  id, campaign_id, placement_key, title, subtitle, cta_label, cta_type,
				  desktop_image_url, display_order, enabled, created_at, updated_at
				) VALUES (
				  ?, ?, 'HOME_HERO', 'Banner VI', 'Phụ đề', 'Mua ngay', 'NONE',
				  '/uploads/promotions/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg', 0, TRUE, NOW(), NOW()
				)
				""",
				placementId, campaignId);

		// Same statements as Liquibase backfill changesets (idempotent NOT EXISTS).
		jdbc.update(
				"""
				INSERT INTO category_translations (id, category_id, locale, name, description)
				SELECT gen_random_uuid(), c.id, 'en', c.name, c.description
				FROM categories c
				WHERE c.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM category_translations t
				    WHERE t.category_id = c.id AND t.locale = 'en'
				  )
				""",
				categoryId);
		jdbc.update(
				"""
				INSERT INTO product_translations (id, product_id, locale, name, description)
				SELECT gen_random_uuid(), p.id, 'en', p.name, p.description
				FROM products p
				WHERE p.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM product_translations t
				    WHERE t.product_id = p.id AND t.locale = 'en'
				  )
				""",
				productId);
		jdbc.update(
				"""
				INSERT INTO promotion_strategy_translations (id, strategy_id, locale, name)
				SELECT gen_random_uuid(), s.id, 'vi', s.name
				FROM promotion_strategies s
				WHERE s.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM promotion_strategy_translations t
				    WHERE t.strategy_id = s.id AND t.locale = 'vi'
				  )
				""",
				strategyId);
		jdbc.update(
				"""
				INSERT INTO promotion_campaign_translations (id, campaign_id, locale, name)
				SELECT gen_random_uuid(), c.id, 'vi', c.name
				FROM promotion_campaigns c
				WHERE c.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM promotion_campaign_translations t
				    WHERE t.campaign_id = c.id AND t.locale = 'vi'
				  )
				""",
				campaignId);
		jdbc.update(
				"""
				INSERT INTO promotion_placement_translations (id, placement_id, locale, title, subtitle, cta_label)
				SELECT gen_random_uuid(), p.id, 'vi', p.title, p.subtitle, p.cta_label
				FROM promotion_placements p
				WHERE p.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM promotion_placement_translations t
				    WHERE t.placement_id = p.id AND t.locale = 'vi'
				  )
				""",
				placementId);

		assertThat(jdbc.queryForObject(
				"SELECT name FROM product_translations WHERE product_id = ? AND locale = 'en'",
				String.class,
				productId)).isEqualTo("Juice");
		assertThat(jdbc.queryForObject(
				"SELECT name FROM category_translations WHERE category_id = ? AND locale = 'en'",
				String.class,
				categoryId)).isEqualTo("Beverages");
		assertThat(jdbc.queryForObject(
				"SELECT name FROM promotion_strategy_translations WHERE strategy_id = ? AND locale = 'vi'",
				String.class,
				strategyId)).isEqualTo("Ưu đãi %");
		assertThat(jdbc.queryForObject(
				"SELECT title FROM promotion_placement_translations WHERE placement_id = ? AND locale = 'vi'",
				String.class,
				placementId)).isEqualTo("Banner VI");

		int before = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_translations WHERE product_id = ?",
				Integer.class,
				productId);
		jdbc.update(
				"""
				INSERT INTO product_translations (id, product_id, locale, name, description)
				SELECT gen_random_uuid(), p.id, 'en', p.name, p.description
				FROM products p
				WHERE p.id = ?
				  AND NOT EXISTS (
				    SELECT 1 FROM product_translations t
				    WHERE t.product_id = p.id AND t.locale = 'en'
				  )
				""",
				productId);
		int after = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_translations WHERE product_id = ?",
				Integer.class,
				productId);
		assertThat(after).isEqualTo(before);
	}

	@Test
	void localeIndexesExist() {
		assertThat(indexExists("idx_product_translations_locale")).isTrue();
		assertThat(indexExists("idx_category_translations_locale")).isTrue();
		assertThat(indexExists("idx_promo_strategy_translations_locale")).isTrue();
		assertThat(indexExists("idx_promo_campaign_translations_locale")).isTrue();
		assertThat(indexExists("idx_promo_placement_translations_locale")).isTrue();
	}

	private boolean tableExists(String table) {
		Integer count = jdbc.queryForObject(
				"""
				SELECT COUNT(*) FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name = ?
				""",
				Integer.class,
				table);
		return count != null && count == 1;
	}

	private boolean indexExists(String indexName) {
		Integer count = jdbc.queryForObject(
				"""
				SELECT COUNT(*) FROM pg_indexes
				WHERE schemaname = 'public' AND indexname = ?
				""",
				Integer.class,
				indexName);
		return count != null && count == 1;
	}

	private List<String> columns(String table) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = ?
				""",
				table);
		return rows.stream().map(r -> String.valueOf(r.get("column_name"))).toList();
	}
}
