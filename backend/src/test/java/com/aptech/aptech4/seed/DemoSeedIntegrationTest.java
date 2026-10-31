package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.support.IntegrationTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DemoSeedIntegrationTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_seed_it")
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
		registry.add("aptech.seed.enabled", () -> "false");
		registry.add("aptech.seed.exit-after", () -> "false");
		registry.add("aptech.seed.admin-password", () -> "admin");
		registry.add("aptech.seed.customer-password", () -> "customer");
		registry.add("aptech.seed.admin-email", () -> "admin@aptech4.local");
		registry.add("aptech.seed.customer-email", () -> "customer@aptech4.local");
	}

	@Autowired
	DemoSeedService demoSeedService;

	@Autowired
	DemoSeedProperties properties;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void seedIsFoodMartOnlyIdempotentAndPurgesRetiredCatalog() {
		assertThat(properties.isEnabled()).isFalse();
		assertThat(DemoSeedCatalog.CATEGORIES).isEmpty();
		assertThat(DemoSeedCatalog.PRODUCTS).isEmpty();

		int expectedCategories = FoodMartCatalog.categories().size();
		int expectedProducts = FoodMartCatalog.products().size();
		assertThat(expectedCategories).isEqualTo(8);
		assertThat(expectedProducts).isEqualTo(48);

		insertRetiredLegacyCatalogRows();

		DemoSeedResult first = demoSeedService.seedAll();
		assertThat(first.usersCreated()).isGreaterThanOrEqualTo(2);
		assertThat(first.categoriesCreated()).isEqualTo(expectedCategories);
		assertThat(first.productsCreated()).isEqualTo(expectedProducts);
		assertThat(first.inventoryUpserts()).isEqualTo(expectedProducts);
		assertThat(first.ordersSeeded()).isGreaterThanOrEqualTo(6);
		assertThat(first.addressesUpserted()).isGreaterThanOrEqualTo(2);
		assertThat(first.retiredProductsRemoved()).isGreaterThanOrEqualTo(1);
		assertThat(first.retiredCategoriesRemoved()).isGreaterThanOrEqualTo(1);

		UUID adminId = userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getId();
		UUID customerId = userAccountRepository.findByEmailWithRoles("customer@aptech4.local").orElseThrow().getId();
		UUID bananaId = productRepository.findBySkuIgnoreCase("FOOD-FRUIT-BANANA-001").orElseThrow().getId();
		UUID fruitsId = categoryRepository.findBySlug("food-fruits-vegetables").orElseThrow().getId();

		var admin = userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow();
		assertThat(admin.getRoles()).extracting(r -> r.getCode()).containsExactly(RoleCode.ADMIN);
		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(admin.getPasswordHash()).doesNotContain("admin");
		assertThat(passwordEncoder.matches("admin", admin.getPasswordHash())).isTrue();

		var customer = userAccountRepository.findByEmailWithRoles("customer@aptech4.local").orElseThrow();
		assertThat(customer.getRoles()).extracting(r -> r.getCode()).containsExactly(RoleCode.CUSTOMER);
		assertThat(passwordEncoder.matches("customer", customer.getPasswordHash())).isTrue();
		assertThat(customerId).isNotEqualTo(adminId);
		assertThat(userAccountRepository.findByEmailWithRoles(DemoSeedMarkers.SECONDARY_CUSTOMER_EMAIL)).isPresent();

		assertThat(productRepository.findBySkuIgnoreCase("DEMO-ELEC-PHONE")).isEmpty();
		assertThat(productRepository.findBySkuIgnoreCase("DEMO-HOME-MUG")).isEmpty();
		assertThat(categoryRepository.findBySlug("demo-electronics")).isEmpty();
		assertThat(categoryRepository.findBySlug("demo-home")).isEmpty();
		assertThat(categoryRepository.findBySlug("demo-archive")).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'DEMO-%'",
				Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from categories where slug like 'demo-%'",
				Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from products where name like 'Demo %'",
				Integer.class)).isZero();

		assertThat(productRepository.findBySkuIgnoreCase("FOOD-FRUIT-APPLE-001")).isPresent();
		assertThat(categoryRepository.findBySlug("food-dairy-eggs")).isPresent();

		Integer oosAvailable = jdbcTemplate.queryForObject(
				"""
						select (i.quantity_on_hand - i.quantity_reserved)
						from products p
						join inventory i on i.product_id = p.id
						where lower(p.sku) = lower('FOOD-BAKERY-FLATBREAD-001')
						""",
				Integer.class
		);
		assertThat(oosAvailable).isNotNull().isLessThanOrEqualTo(0);

		Integer negativeStock = jdbcTemplate.queryForObject(
				"select count(*) from inventory where quantity_on_hand < 0 or quantity_reserved < 0",
				Integer.class);
		assertThat(negativeStock).isZero();

		Integer userCount = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
		Integer foodProductCount = jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'FOOD-%'",
				Integer.class);
		assertThat(foodProductCount).isEqualTo(expectedProducts);

		String originalAdminHash = admin.getPasswordHash();
		DemoSeedResult second = demoSeedService.seedAll();
		assertThat(second.usersCreated()).isZero();
		assertThat(second.usersUpdated()).isGreaterThanOrEqualTo(2);
		assertThat(second.ordersSeeded()).isZero();
		assertThat(second.categoriesCreated()).isZero();
		assertThat(second.productsCreated()).isZero();
		assertThat(second.retiredProductsRemoved()).isZero();
		assertThat(second.retiredCategoriesRemoved()).isZero();

		assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class)).isEqualTo(userCount);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'FOOD-%'",
				Integer.class)).isEqualTo(foodProductCount);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'DEMO-%'",
				Integer.class)).isZero();
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getPasswordHash())
				.isEqualTo(originalAdminHash);
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getId())
				.isEqualTo(adminId);
		assertThat(productRepository.findBySkuIgnoreCase("FOOD-FRUIT-BANANA-001").orElseThrow().getId()).isEqualTo(bananaId);
		assertThat(categoryRepository.findBySlug("food-fruits-vegetables").orElseThrow().getId()).isEqualTo(fruitsId);
	}

	private void insertRetiredLegacyCatalogRows() {
		Category electronics = Category.createNew("Electronics", "demo-electronics", "legacy", null);
		categoryRepository.save(electronics);
		Product phone = Product.createNew(
				electronics,
				"DEMO-ELEC-PHONE",
				"Demo Smartphone X",
				"legacy seed remnant",
				new BigDecimal("12990000.00"),
				"VND",
				true
		);
		productRepository.save(phone);
	}
}
