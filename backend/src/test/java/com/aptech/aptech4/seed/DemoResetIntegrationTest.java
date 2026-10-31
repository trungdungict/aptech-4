package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
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
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.support.IntegrationTestSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DemoResetIntegrationTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_reset_it")
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
		registry.add("aptech.seed.reset", () -> "false");
		registry.add("aptech.seed.exit-after", () -> "false");
		registry.add("aptech.seed.admin-password", () -> "admin");
		registry.add("aptech.seed.customer-password", () -> "customer");
		registry.add("aptech.seed.admin-email", () -> "admin@aptech4.local");
		registry.add("aptech.seed.customer-email", () -> "customer@aptech4.local");
	}

	@Autowired
	DemoSeedService demoSeedService;

	@Autowired
	DemoResetService demoResetService;

	@Autowired
	DemoSeedProperties properties;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void dryRunPlanDoesNotMutateDatabase() {
		demoSeedService.seedAll();
		int usersBefore = count("select count(*) from users");
		int productsBefore = count("select count(*) from products");

		int expectedCategories = FoodMartCatalog.categories().size();
		int expectedProducts = FoodMartCatalog.products().size();

		DemoResetPlan plan = demoResetService.plan();
		assertThat(plan.jdbcUrl()).contains("aptech4_reset_it");
		assertThat(plan.demoUsers()).isEqualTo(3);
		assertThat(plan.demoCategories()).isEqualTo(expectedCategories);
		assertThat(plan.demoProducts()).isEqualTo(expectedProducts);
		assertThat(plan.demoEmails()).containsExactlyInAnyOrder(
				"admin@aptech4.local",
				"customer@aptech4.local",
				DemoSeedMarkers.SECONDARY_CUSTOMER_EMAIL
		);

		assertThat(count("select count(*) from users")).isEqualTo(usersBefore);
		assertThat(count("select count(*) from products")).isEqualTo(productsBefore);
	}

	@Test
	void resetDeletesDemoKeepsNonDemoRolesAndIsIdempotentThenReseedWorks() {
		demoSeedService.seedAll();
		NonDemoFixture nonDemo = insertNonDemoRows();

		int rolesBefore = count("select count(*) from roles");
		assertThat(rolesBefore).isGreaterThanOrEqualTo(2);

		assertThatThrownBy(() -> demoResetService.execute(""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reset-confirm");
		assertThatThrownBy(() -> demoResetService.execute("YES"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reset-confirm");
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local")).isPresent();

		int expectedCategories = FoodMartCatalog.categories().size();
		int expectedProducts = FoodMartCatalog.products().size();

		DemoResetResult first = demoResetService.execute("yes");
		assertThat(first.usersDeleted()).isEqualTo(3);
		assertThat(first.categoriesDeleted()).isEqualTo(expectedCategories);
		assertThat(first.productsDeleted()).isEqualTo(expectedProducts);

		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local")).isEmpty();
		assertThat(userAccountRepository.findByEmailWithRoles("customer@aptech4.local")).isEmpty();
		assertThat(userAccountRepository.findByEmailWithRoles(DemoSeedMarkers.SECONDARY_CUSTOMER_EMAIL)).isEmpty();
		assertThat(categoryRepository.findBySlug("food-fruits-vegetables")).isEmpty();
		assertThat(productRepository.findBySkuIgnoreCase("FOOD-FRUIT-BANANA-001")).isEmpty();
		assertThat(productRepository.findBySkuIgnoreCase("DEMO-ELEC-PHONE")).isEmpty();

		assertThat(userAccountRepository.findByEmailWithRoles(nonDemo.email())).isPresent();
		assertThat(categoryRepository.findBySlug(nonDemo.categorySlug())).isPresent();
		assertThat(productRepository.findBySkuIgnoreCase(nonDemo.productSku())).isPresent();
		assertThat(count("select count(*) from roles")).isEqualTo(rolesBefore);

		DemoResetResult second = demoResetService.execute("yes");
		assertThat(second.usersDeleted()).isZero();
		assertThat(second.productsDeleted()).isZero();
		assertThat(second.categoriesDeleted()).isZero();
		assertThat(userAccountRepository.findByEmailWithRoles(nonDemo.email())).isPresent();

		DemoSeedResult reseed = demoSeedService.seedAll();
		assertThat(reseed.usersCreated()).isEqualTo(3);
		assertThat(reseed.categoriesCreated()).isEqualTo(expectedCategories);
		assertThat(reseed.productsCreated()).isEqualTo(expectedProducts);
		assertThat(userAccountRepository.findByEmailWithRoles("admin@aptech4.local")).isPresent();
		assertThat(productRepository.findBySkuIgnoreCase("DEMO-ELEC-PHONE")).isEmpty();
		assertThat(productRepository.findBySkuIgnoreCase("FOOD-DAIRY-MILK-001")).isPresent();
		assertThat(categoryRepository.findBySlug("demo-electronics")).isEmpty();
		assertThat(userAccountRepository.findByEmailWithRoles(nonDemo.email())).isPresent();
		assertThat(count("select count(*) from roles")).isEqualTo(rolesBefore);

		Integer fkCount = jdbcTemplate.queryForObject(
				"""
						select count(*) from information_schema.table_constraints
						where constraint_type = 'FOREIGN KEY'
						  and table_schema = current_schema()
						""",
				Integer.class
		);
		assertThat(fkCount).isNotNull().isGreaterThan(10);
	}

	@Test
	void prodProfileRefusesPlanAndExecute() {
		MockEnvironment prod = new MockEnvironment();
		prod.setActiveProfiles("prod");
		assertThatThrownBy(() -> DemoSeedGuard.assertAllowed(prod, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prod");
	}

	private NonDemoFixture insertNonDemoRows() {
		Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
		UserAccount realUser = UserAccount.createNew(
				"real.customer@example.com",
				passwordEncoder.encode("RealUser_not_demo!"),
				"Real Customer"
		);
		realUser.setRoles(Set.of(customer));
		userAccountRepository.save(realUser);

		Category realCategory = Category.createNew("Real Electronics", "real-electronics", "Not demo", null);
		categoryRepository.save(realCategory);

		Product realProduct = Product.createNew(
				realCategory,
				"REAL-PHONE-01",
				"Real Phone",
				"Non-demo SKU",
				new BigDecimal("1000000.00"),
				"VND",
				true
		);
		productRepository.save(realProduct);

		return new NonDemoFixture(
				"real.customer@example.com",
				"real-electronics",
				"REAL-PHONE-01",
				realUser.getId(),
				realCategory.getId(),
				realProduct.getId()
		);
	}

	private int count(String sql) {
		Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
		return value == null ? 0 : value;
	}

	private record NonDemoFixture(
			String email,
			String categorySlug,
			String productSku,
			UUID userId,
			UUID categoryId,
			UUID productId
	) {
	}
}
