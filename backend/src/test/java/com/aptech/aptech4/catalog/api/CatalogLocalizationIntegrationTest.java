package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.CategoryTranslationRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.catalog.persistence.ProductTranslationRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogLocalizationIntegrationTest {

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
	JwtService jwtService;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	InventoryRepository inventoryRepository;

	@Autowired
	ProductTranslationRepository productTranslationRepository;

	@Autowired
	CategoryTranslationRepository categoryTranslationRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private String suffix;
	private UUID categoryId;
	private UUID productId;
	private String sku;
	private BigDecimal price;

	@BeforeEach
	void seed() {
		suffix = UUID.randomUUID().toString().substring(0, 8);
		sku = "LOC-" + suffix;
		price = new BigDecimal("199000.00");
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Danh mục VI " + suffix,
					"cat-loc-" + suffix,
					"Mô tả danh mục VI",
					null
			));
			categoryId = category.getId();
			Product product = productRepository.saveAndFlush(Product.createNew(
					category,
					sku,
					"Sản phẩm VI " + suffix,
					"Mô tả sản phẩm VI",
					price,
					"VND",
					true
			));
			productId = product.getId();
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, 3));
			productTranslationRepository.saveAndFlush(com.aptech.aptech4.catalog.domain.ProductTranslation.createNew(
					product, "vi", product.getName(), product.getDescription()
			));
			productTranslationRepository.saveAndFlush(com.aptech.aptech4.catalog.domain.ProductTranslation.createNew(
					product, "en", "Product EN " + suffix, "Product description EN"
			));
			categoryTranslationRepository.saveAndFlush(com.aptech.aptech4.catalog.domain.CategoryTranslation.createNew(
					category, "vi", category.getName(), category.getDescription()
			));
			categoryTranslationRepository.saveAndFlush(com.aptech.aptech4.catalog.domain.CategoryTranslation.createNew(
					category, "en", "Category EN " + suffix, "Category description EN"
			));
		});
	}

	@Test
	void publicProductAndCategoryResolveViEnAndFallback() throws Exception {
		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId.toString()))
				.andExpect(jsonPath("$.sku").value(sku))
				.andExpect(jsonPath("$.name").value("Sản phẩm VI " + suffix))
				.andExpect(jsonPath("$.description").value("Mô tả sản phẩm VI"))
				.andExpect(jsonPath("$.unitPrice").value(199000.0))
				.andExpect(jsonPath("$.categoryName").value("Danh mục VI " + suffix));

		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId.toString()))
				.andExpect(jsonPath("$.sku").value(sku))
				.andExpect(jsonPath("$.name").value("Product EN " + suffix))
				.andExpect(jsonPath("$.description").value("Product description EN"))
				.andExpect(jsonPath("$.unitPrice").value(199000.0))
				.andExpect(jsonPath("$.categoryName").value("Category EN " + suffix));

		mockMvc.perform(get("/api/v1/categories/{id}", categoryId).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Category EN " + suffix))
				.andExpect(jsonPath("$.slug").value("cat-loc-" + suffix));

		// Missing EN translation → canonical VI fallback (create product without EN row)
		UUID[] fallbackId = new UUID[1];
		String fallbackSku = "FB-" + suffix;
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.findById(categoryId).orElseThrow();
			Product onlyVi = productRepository.saveAndFlush(Product.createNew(
					category, fallbackSku, "Chỉ VI " + suffix, "Desc VI only", price, "VND", true
			));
			fallbackId[0] = onlyVi.getId();
			inventoryRepository.saveAndFlush(Inventory.forProduct(onlyVi, 1));
		});

		mockMvc.perform(get("/api/v1/products/{id}", fallbackId[0]).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Chỉ VI " + suffix))
				.andExpect(jsonPath("$.description").value("Desc VI only"))
				.andExpect(jsonPath("$.sku").value(fallbackSku));

		// Unsupported language → VI default
		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sản phẩm VI " + suffix));
	}

	@Test
	void adminTranslationsCrudValidationAndRbac() throws Exception {
		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.loc@example.com", List.of("ADMIN"));
		String customer = jwtService.createAccessToken(UUID.randomUUID(), "cust.loc@example.com", List.of("CUSTOMER"));

		mockMvc.perform(get("/api/v1/admin/products/{id}/translations", productId))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/products/{id}/translations", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/products/{id}/translations", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(productId.toString()))
				.andExpect(jsonPath("$.translations.vi.name").value("Sản phẩm VI " + suffix))
				.andExpect(jsonPath("$.translations.en.name").value("Product EN " + suffix));

		mockMvc.perform(put("/api/v1/admin/products/{id}/translations", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "translations": {
								    "vi": { "name": "Sản phẩm mới VI %s", "description": "Mô tả mới" },
								    "en": { "name": "New Product EN %s", "description": "New EN desc" }
								  }
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.translations.vi.name").value("Sản phẩm mới VI " + suffix))
				.andExpect(jsonPath("$.translations.en.name").value("New Product EN " + suffix));

		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("New Product EN " + suffix))
				.andExpect(jsonPath("$.sku").value(sku));

		mockMvc.perform(put("/api/v1/admin/products/{id}/translations", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "translations": { "fr": { "name": "Non" } } }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Bad Request"));

		mockMvc.perform(put("/api/v1/admin/products/{id}/translations", productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "translations": { "en": { "name": "Only EN" } } }
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/categories/{id}/translations", categoryId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "translations": {
								    "vi": { "name": "Danh mục mới %s", "description": "OK" },
								    "en": { "name": "New Category %s", "description": "OK EN" }
								  }
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.translations.en.name").value("New Category " + suffix));
	}

	@Test
	void localizedNotFoundMessageKeepsStableErrorCode() throws Exception {
		UUID missing = UUID.randomUUID();
		mockMvc.perform(get("/api/v1/products/{id}", missing).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm"));

		mockMvc.perform(get("/api/v1/products/{id}", missing).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("Not Found"))
				.andExpect(jsonPath("$.message").value("Product not found"));
	}

	@Test
	void localeSpecificCachesDoNotCrossContaminate() throws Exception {
		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
				.andExpect(jsonPath("$.name").value("Sản phẩm VI " + suffix));
		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(jsonPath("$.name").value("Product EN " + suffix));
		// Re-hit VI after EN — must not leak EN name
		mockMvc.perform(get("/api/v1/products/{id}", productId).header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
				.andExpect(jsonPath("$.name").value("Sản phẩm VI " + suffix));
	}

	@Test
	void publicProductListResolvesEnAndMissingEnFallback() throws Exception {
		// Missing-EN fixture (same pattern as seeded FOOD-FROZEN-PEAS-001) — no EN row.
		String fallbackSku = "LIST-FB-" + suffix;
		String fallbackViName = "Chỉ VI list " + suffix;
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.findById(categoryId).orElseThrow();
			Product onlyVi = productRepository.saveAndFlush(Product.createNew(
					category, fallbackSku, fallbackViName, "Desc VI only", price, "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(onlyVi, 1));
		});

		// Public list `q` matches base name/description (not SKU) — search by canonical VI name.
		assertListProductNameBySku(sku, "Sản phẩm VI " + suffix, "en", "Product EN " + suffix);
		assertListProductNameBySku(fallbackSku, fallbackViName, "en", fallbackViName);
	}

	private void assertListProductNameBySku(
			String productSku,
			String searchName,
			String acceptLanguage,
			String expectedName
	) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/products")
						.param("q", searchName)
						.param("categoryId", categoryId.toString())
						.param("page", "0")
						.param("size", "50")
						.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		String actual = null;
		for (JsonNode item : items) {
			if (productSku.equals(item.get("sku").asText())) {
				actual = item.get("name").asText();
				break;
			}
		}
		assertThat(actual)
				.as("list name for sku=%s under Accept-Language=%s", productSku, acceptLanguage)
				.isEqualTo(expectedName);
	}
}
