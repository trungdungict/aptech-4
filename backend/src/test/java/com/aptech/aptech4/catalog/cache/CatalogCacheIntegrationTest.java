package com.aptech.aptech4.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.aptech.aptech4.catalog.api.CategoryResponse;
import com.aptech.aptech4.catalog.api.ProductResponse;
import com.aptech.aptech4.catalog.application.CategoryService;
import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.support.IntegrationTestSupport;

import jakarta.persistence.EntityManagerFactory;

/**
 * Public catalog Redis cache hit/miss/invalidation (ADR-015 / P13-T01+T02).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CatalogCacheIntegrationTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_cache_it")
			.withUsername("aptech")
			.withPassword("aptech_tc");

	@Container
	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
			.withExposedPorts(6379)
			.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
					.withStartupTimeout(Duration.ofSeconds(60)));

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
		// Testcontainers Redis has no AUTH. Clear host REDIS_PASSWORD so stack .env cannot break ITs.
		registry.add("spring.data.redis.password", () -> "");
		IntegrationTestSupport.registerSecurityProperties(registry);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ProductService productService;

	@Autowired
	CategoryService categoryService;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	CatalogCacheInvalidator catalogCacheInvalidator;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	InventoryRepository inventoryRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	JwtService jwtService;

	@Autowired
	RedisConnectionFactory redisConnectionFactory;

	private UUID categoryId;
	private UUID productId;
	private String suffix;

	@BeforeEach
	void seed() {
		assertRedisPing();
		suffix = UUID.randomUUID().toString().substring(0, 8);
		catalogCacheInvalidator.onCategoryMutation(null);
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Cache Cat " + suffix, "cache-cat-" + suffix, "cache", null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category,
					"CACHE-" + suffix,
					"Cached Tea " + suffix,
					"desc",
					new BigDecimal("15000"),
					"VND",
					true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, 5));
			categoryId = category.getId();
			productId = product.getId();
		});
	}

	@Test
	void productDetailCacheMissThenHitSkipsDb() {
		Statistics stats = hibernateStats();
		stats.clear();

		ProductResponse first = productService.getPublic(productId);
		assertThat(first.id()).isEqualTo(productId);
		long afterMiss = stats.getPrepareStatementCount();
		assertThat(afterMiss).isPositive();

		requireProductDetailCached(productId);

		stats.clear();
		ProductResponse second = productService.getPublic(productId);
		assertThat(second).isEqualTo(first);
		assertThat(stats.getPrepareStatementCount()).isZero();
	}

	@Test
	void productListKeysDoNotCollideAcrossQueryParams() {
		PageResponse<ProductResponse> plain = productService.listPublic(
				null, null, null, null, null, 0, 20, null
		);
		PageResponse<ProductResponse> filtered = productService.listPublic(
				"Cached Tea", categoryId, null, null, true, 0, 20, "name,asc"
		);
		assertThat(plain.totalElements()).isPositive();
		assertThat(filtered.totalElements()).isPositive();

		Cache cache = cacheManager.getCache(CatalogCacheNames.PRODUCTS_LIST);
		assertThat(cache).isNotNull();
		String keyA = CatalogCacheKeys.productList(null, null, null, null, null, 0, 20, null);
		String keyB = CatalogCacheKeys.productList(
				"Cached Tea", categoryId, null, null, true, 0, 20, "name,asc"
		);
		assertThat(keyA).isNotEqualTo(keyB);
		assertThat(cache.get(keyA)).isNotNull();
		assertThat(cache.get(keyB)).isNotNull();
	}

	@Test
	void categoryListCachedAndInvalidatedOnDisable() throws Exception {
		List<CategoryResponse> first = categoryService.listPublicActive();
		assertThat(first.stream().anyMatch(c -> c.id().equals(categoryId))).isTrue();

		Cache cache = cacheManager.getCache(CatalogCacheNames.CATEGORIES_LIST);
		assertThat(cache).isNotNull();
		assertThat(cache.get(CatalogCacheKeys.categoriesAll())).isNotNull();

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.cache@example.com", List.of("ADMIN"));

		mockMvc.perform(patch("/api/v1/admin/categories/{id}/status", categoryId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk());

		assertThat(cache.get(CatalogCacheKeys.categoriesAll())).isNull();
		List<CategoryResponse> after = categoryService.listPublicActive();
		assertThat(after.stream().noneMatch(c -> c.id().equals(categoryId))).isTrue();

		mockMvc.perform(get("/api/v1/categories/{id}", categoryId))
				.andExpect(status().isNotFound());
	}

	@Test
	void productDisableInvalidatesDetailCache() throws Exception {
		productService.getPublic(productId);
		requireProductDetailCached(productId);
		Cache detail = cacheManager.getCache(CatalogCacheNames.PRODUCTS_DETAIL);

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.cache@example.com", List.of("ADMIN"));
		mockMvc.perform(patch("/api/v1/admin/products/{id}/status", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isOk());

		assertThat(detail.get(CatalogCacheKeys.productDetail(productId))).isNull();
		assertThatThrownBy(() -> productService.getPublic(productId))
				.isInstanceOf(ApiException.class);
		mockMvc.perform(get("/api/v1/products/{id}", productId))
				.andExpect(status().isNotFound());
	}

	@Test
	void cacheErrorHandlerAllowsDirectServiceReadWhenCacheClearedManually() {
		// Sanity: after clear, service still serves from Postgres (source of truth).
		productService.getPublic(productId);
		catalogCacheInvalidator.onProductMutation(productId);
		ProductResponse again = productService.getPublic(productId);
		assertThat(again.id()).isEqualTo(productId);
	}

	private Statistics hibernateStats() {
		SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
		Statistics stats = sessionFactory.getStatistics();
		stats.setStatisticsEnabled(true);
		return stats;
	}

	/**
	 * Direct Redis PING (bypasses CacheErrorHandler). Fails loudly when the
	 * Testcontainers Redis socket is down instead of a later null {@code cache.get}.
	 */
	private void assertRedisPing() {
		try (RedisConnection connection = redisConnectionFactory.getConnection()) {
			String pong = connection.ping();
			assertThat(pong).as("Redis PING").isEqualTo("PONG");
			byte[] probeKey = ("it-redis-probe-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
			connection.stringCommands().set(probeKey, "ok".getBytes(StandardCharsets.UTF_8));
			assertThat(connection.stringCommands().get(probeKey))
					.as("Redis SET/GET probe")
					.isNotNull();
			connection.keyCommands().del(probeKey);
		}
	}

	/**
	 * Production CacheErrorHandler swallows Redis PUT failures. If Lettuce blipped during
	 * {@code @Cacheable} put, re-PING and re-drive one miss→put so the assertion measures
	 * cache behavior rather than a transient socket error. Still requires a non-null entry.
	 */
	private void requireProductDetailCached(UUID id) {
		Cache cache = cacheManager.getCache(CatalogCacheNames.PRODUCTS_DETAIL);
		assertThat(cache).as("PRODUCTS_DETAIL cache region").isNotNull();
		Object detailKey = CatalogCacheKeys.productDetail(id);
		if (cache.get(detailKey) == null) {
			assertRedisPing();
			cache.evict(detailKey);
			ProductResponse again = productService.getPublic(id);
			assertThat(again.id()).isEqualTo(id);
		}
		assertThat(cache.get(detailKey))
				.as("PRODUCTS_DETAIL must contain key after successful @Cacheable put")
				.isNotNull();
	}
}
