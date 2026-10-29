package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
class ProductIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		String pgHost = env("IT_POSTGRES_HOST", "localhost");
		String pgPort = env("IT_POSTGRES_PORT", "5432");
		String pgDb = env("IT_POSTGRES_DB", "aptech4_catalog_it");
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
		registry.add("aptech.security.cors.allowed-origins", () -> "http://localhost:5173");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	InventoryRepository inventoryRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	EntityManager entityManager;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String suffix;
	private UUID activeCategoryId;
	private UUID inactiveCategoryId;
	private UUID alphaId;
	private UUID betaId;
	private UUID inactiveProductId;
	private UUID orphanCategoryProductId;

	@BeforeEach
	void seed() {
		suffix = UUID.randomUUID().toString().substring(0, 8);
		transactionTemplate.executeWithoutResult(status -> {
			Category active = categoryRepository.saveAndFlush(Category.createNew(
					"Active Cat " + suffix, "active-cat-" + suffix, "active", null
			));
			Category inactive = Category.createNew(
					"Inactive Cat " + suffix, "inactive-cat-" + suffix, "inactive", null
			);
			inactive.setActive(false);
			inactive = categoryRepository.saveAndFlush(inactive);

			activeCategoryId = active.getId();
			inactiveCategoryId = inactive.getId();

			Product alpha = productRepository.saveAndFlush(Product.createNew(
					active, "SKU-A-" + suffix, "Alpha Widget " + suffix, "Alpha desc",
					new BigDecimal("100.00"), "VND", true
			));
			Product beta = productRepository.saveAndFlush(Product.createNew(
					active, "SKU-B-" + suffix, "Beta Gadget " + suffix, "Beta desc",
					new BigDecimal("250.50"), "VND", true
			));
			Product inactiveProduct = productRepository.saveAndFlush(Product.createNew(
					active, "SKU-X-" + suffix, "Hidden Widget " + suffix, "Hidden",
					new BigDecimal("50.00"), "VND", false
			));
			Product onInactiveCategory = productRepository.saveAndFlush(Product.createNew(
					inactive, "SKU-Y-" + suffix, "Orphan Widget " + suffix, "Should hide",
					new BigDecimal("75.00"), "VND", true
			));

			inventoryRepository.saveAndFlush(Inventory.forProduct(alpha, 5));
			inventoryRepository.saveAndFlush(Inventory.forProduct(beta, 0));
			inventoryRepository.saveAndFlush(Inventory.forProduct(inactiveProduct, 9));
			inventoryRepository.saveAndFlush(Inventory.forProduct(onInactiveCategory, 3));

			alphaId = alpha.getId();
			betaId = beta.getId();
			inactiveProductId = inactiveProduct.getId();
			orphanCategoryProductId = onInactiveCategory.getId();

			entityManager.flush();
			entityManager.clear();
		});
	}

	@Test
	void publicListHidesInactiveAndSupportsSearchFilterPaginationSort() throws Exception {
		MvcResult list = mockMvc.perform(get("/api/v1/products")
				.param("q", "Widget " + suffix)
				.param("page", "0")
				.param("size", "10")
				.param("sort", "name,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.items").isArray())
			.andReturn();

		JsonNode body = objectMapper.readTree(list.getResponse().getContentAsString());
		assertThat(body.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
		boolean sawAlpha = false;
		for (JsonNode item : body.get("items")) {
			String id = item.get("id").asText();
			assertThat(id).isNotEqualTo(inactiveProductId.toString());
			assertThat(id).isNotEqualTo(orphanCategoryProductId.toString());
			assertThat(item.has("password")).isFalse();
			assertThat(item.has("createdAt")).isFalse();
			assertThat(item.get("imageUrl").isNull()).isTrue();
			assertThat(item.get("unitPrice").isNumber() || item.get("unitPrice").isTextual()).isTrue();
			if (alphaId.toString().equals(id)) {
				sawAlpha = true;
				assertThat(item.get("name").asText()).contains("Alpha Widget");
				assertThat(item.get("inStock").asBoolean()).isTrue();
				assertThat(item.get("categoryId").asText()).isEqualTo(activeCategoryId.toString());
			}
		}
		assertThat(sawAlpha).isTrue();

		mockMvc.perform(get("/api/v1/products/" + alphaId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(alphaId.toString()))
			.andExpect(jsonPath("$.sku").value("SKU-A-" + suffix))
			.andExpect(jsonPath("$.unitPrice").value(100.00))
			.andExpect(jsonPath("$.currency").value("VND"))
			.andExpect(jsonPath("$.inStock").value(true));

		mockMvc.perform(get("/api/v1/products/" + inactiveProductId))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/products/" + orphanCategoryProductId))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	@Test
	void categoryAndPriceAndStockFilters() throws Exception {
		mockMvc.perform(get("/api/v1/products")
				.param("categoryId", activeCategoryId.toString())
				.param("q", suffix)
				.param("minPrice", "200")
				.param("maxPrice", "300"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(betaId.toString()));

		mockMvc.perform(get("/api/v1/products")
				.param("categoryId", inactiveCategoryId.toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Category not found"));

		mockMvc.perform(get("/api/v1/products")
				.param("categoryId", UUID.randomUUID().toString()))
			.andExpect(status().isNotFound());

		MvcResult inStock = mockMvc.perform(get("/api/v1/products")
				.param("q", suffix)
				.param("inStock", "true")
				.param("categoryId", activeCategoryId.toString()))
			.andExpect(status().isOk())
			.andReturn();
		for (JsonNode item : objectMapper.readTree(inStock.getResponse().getContentAsString()).get("items")) {
			assertThat(item.get("id").asText()).isEqualTo(alphaId.toString());
			assertThat(item.get("inStock").asBoolean()).isTrue();
		}

		MvcResult outOfStock = mockMvc.perform(get("/api/v1/products")
				.param("q", "Beta Gadget " + suffix)
				.param("inStock", "false"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode outItems = objectMapper.readTree(outOfStock.getResponse().getContentAsString()).get("items");
		assertThat(outItems).isNotEmpty();
		assertThat(outItems.get(0).get("id").asText()).isEqualTo(betaId.toString());
		assertThat(outItems.get(0).get("inStock").asBoolean()).isFalse();
	}

	@Test
	void paginationMetadataAndValidation() throws Exception {
		mockMvc.perform(get("/api/v1/products")
				.param("q", suffix)
				.param("categoryId", activeCategoryId.toString())
				.param("page", "0")
				.param("size", "1")
				.param("sort", "price,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.first").value(true))
			.andExpect(jsonPath("$.last").value(false))
			.andExpect(jsonPath("$.items[0].id").value(alphaId.toString()));

		mockMvc.perform(get("/api/v1/products")
				.param("q", suffix)
				.param("categoryId", activeCategoryId.toString())
				.param("page", "1")
				.param("size", "1")
				.param("sort", "price,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(betaId.toString()))
			.andExpect(jsonPath("$.last").value(true));

		// Default page size is 20 when omitted (zero-based page still 0).
		mockMvc.perform(get("/api/v1/products")
				.param("q", suffix)
				.param("categoryId", activeCategoryId.toString())
				.param("page", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(2));

		// Whitelist includes createdAt (stable with id tie-breaker).
		mockMvc.perform(get("/api/v1/products")
				.param("q", suffix)
				.param("categoryId", activeCategoryId.toString())
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2));

		mockMvc.perform(get("/api/v1/products").param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size must be <= 100"));

		mockMvc.perform(get("/api/v1/products").param("sort", "sku,asc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid sort field. Allowed: name, price, createdAt"));

		mockMvc.perform(get("/api/v1/products").param("minPrice", "-1"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/products")
				.param("minPrice", "10")
				.param("maxPrice", "5"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("minPrice must be <= maxPrice"));

		mockMvc.perform(get("/api/v1/products").param("page", "-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void openApiIncludesProductPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/products']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/products/{id}']").exists());
	}
}
