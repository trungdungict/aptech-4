package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThat;

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
		registry.add("aptech.seed.admin-password", () -> "AdminDemo_change_me!");
		registry.add("aptech.seed.customer-password", () -> "CustomerDemo_change_me!");
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
	void seedIsIdempotentAndHashesPasswords() {
		assertThat(properties.isEnabled()).isFalse();

		DemoSeedResult first = demoSeedService.seedAll();
		assertThat(first.usersCreated()).isEqualTo(2);
		assertThat(first.categoriesCreated()).isEqualTo(DemoSeedCatalog.CATEGORIES.size());
		assertThat(first.productsCreated()).isEqualTo(DemoSeedCatalog.PRODUCTS.size());
		assertThat(first.inventoryUpserts()).isEqualTo(DemoSeedCatalog.PRODUCTS.size());

		UUID adminId = userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getId();
		UUID customerId = userAccountRepository.findByEmailWithRoles("customer@aptech4.local").orElseThrow().getId();
		UUID phoneId = productRepository.findBySkuIgnoreCase("DEMO-ELEC-PHONE").orElseThrow().getId();
		UUID electronicsId = categoryRepository.findBySlug("demo-electronics").orElseThrow().getId();

		var admin = userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow();
		assertThat(admin.getRoles()).extracting(r -> r.getCode()).containsExactly(RoleCode.ADMIN);
		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(admin.getPasswordHash()).doesNotContain("AdminDemo_change_me!");
		assertThat(passwordEncoder.matches("AdminDemo_change_me!", admin.getPasswordHash())).isTrue();

		var customer = userAccountRepository.findByEmailWithRoles("customer@aptech4.local").orElseThrow();
		assertThat(customer.getRoles()).extracting(r -> r.getCode()).containsExactly(RoleCode.CUSTOMER);
		assertThat(customerId).isNotEqualTo(adminId);

		Category archive = categoryRepository.findBySlug("demo-archive").orElseThrow();
		assertThat(archive.isActive()).isFalse();
		assertThat(productRepository.findBySkuIgnoreCase("DEMO-HOME-OOS")).isPresent();
		Product inactive = productRepository.findBySkuIgnoreCase("DEMO-ELEC-OLD").orElseThrow();
		assertThat(inactive.isActive()).isFalse();

		Integer negativeStock = jdbcTemplate.queryForObject(
				"select count(*) from inventory where quantity_on_hand < 0 or quantity_reserved < 0",
				Integer.class);
		assertThat(negativeStock).isZero();

		Integer userCount = jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
		Integer productCount = jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'DEMO-%'",
				Integer.class);

		String originalAdminHash = admin.getPasswordHash();
		DemoSeedResult second = demoSeedService.seedAll();
		assertThat(second.usersCreated()).isZero();
		assertThat(second.usersUpdated()).isEqualTo(2);
		assertThat(second.categoriesCreated()).isZero();
		assertThat(second.productsCreated()).isZero();

		assertThat(jdbcTemplate.queryForObject("select count(*) from users", Integer.class)).isEqualTo(userCount);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from products where sku like 'DEMO-%'",
				Integer.class)).isEqualTo(productCount);
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getPasswordHash())
				.isEqualTo(originalAdminHash);
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local").orElseThrow().getId())
				.isEqualTo(adminId);
		assertThat(productRepository.findBySkuIgnoreCase("DEMO-ELEC-PHONE").orElseThrow().getId()).isEqualTo(phoneId);
		assertThat(categoryRepository.findBySlug("demo-electronics").orElseThrow().getId()).isEqualTo(electronicsId);
	}
}
