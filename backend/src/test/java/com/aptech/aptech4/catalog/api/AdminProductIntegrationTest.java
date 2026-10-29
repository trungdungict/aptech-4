package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductIntegrationTest {

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
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void adminCrudRbacValidationAndSoftDisable() throws Exception {
		String admin = adminToken();
		String customer = customerToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		UUID[] ids = new UUID[2];
		transactionTemplate.executeWithoutResult(status -> {
			Category active = categoryRepository.saveAndFlush(Category.createNew(
					"Admin Prod Cat " + suffix, "admin-prod-cat-" + suffix, null, null
			));
			Category inactive = Category.createNew(
					"Inactive Prod Cat " + suffix, "inactive-prod-cat-" + suffix, null, null
			);
			inactive.setActive(false);
			inactive = categoryRepository.saveAndFlush(inactive);
			ids[0] = active.getId();
			ids[1] = inactive.getId();
		});
		UUID activeCategoryId = ids[0];
		UUID inactiveCategoryId = ids[1];

		mockMvc.perform(get("/api/v1/admin/products"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
			.andExpect(status().isForbidden());

		String sku = "ADM-" + suffix;
		MvcResult created = mockMvc.perform(post("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "%s",
						  "name": "Admin Widget %s",
						  "description": "Created by admin",
						  "unitPrice": 199.99,
						  "currency": "vnd",
						  "categoryId": "%s",
						  "quantityOnHand": 999,
						  "inventory": { "quantityOnHand": 999 },
						  "active": false,
						  "id": "%s"
						}
						""".formatted(sku, suffix, activeCategoryId, UUID.randomUUID())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sku").value(sku))
			.andExpect(jsonPath("$.name").value("Admin Widget " + suffix))
			.andExpect(jsonPath("$.currency").value("VND"))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.quantityOnHand").doesNotExist())
			.andExpect(jsonPath("$.unitPrice").value(199.99))
			.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
		String productId = body.get("id").asText();
		assertThat(body.get("inStock").isNull()).isTrue();
		assertThat(body.has("inventory")).isFalse();

		boolean inventoryCreated = transactionTemplate.execute(status ->
				inventoryRepository.findById(UUID.fromString(productId)).isPresent()
		);
		assertThat(inventoryCreated).isFalse();

		mockMvc.perform(get("/api/v1/admin/products/" + productId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(get("/api/v1/products/" + productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(productId));

		mockMvc.perform(put("/api/v1/admin/products/" + productId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "%s",
						  "name": "Admin Widget %s Updated",
						  "description": "Updated",
						  "unitPrice": 249.50,
						  "categoryId": "%s",
						  "quantityOnHand": 1
						}
						""".formatted(sku, suffix, activeCategoryId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Admin Widget " + suffix + " Updated"))
			.andExpect(jsonPath("$.unitPrice").value(249.50));

		JsonNode updated = objectMapper.readTree(
				mockMvc.perform(get("/api/v1/admin/products/" + productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString()
		);
		assertThat(updated.get("inStock").isNull()).isTrue();

		Boolean inventoryPresent = transactionTemplate.execute(status ->
				inventoryRepository.findById(UUID.fromString(productId)).isPresent()
		);
		assertThat(inventoryPresent).isFalse();

		mockMvc.perform(post("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "%s",
						  "name": "Dup",
						  "unitPrice": 10,
						  "categoryId": "%s"
						}
						""".formatted(sku.toLowerCase(), activeCategoryId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Product SKU already exists"));

		mockMvc.perform(post("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "BAD-%s",
						  "name": "Neg",
						  "unitPrice": -1,
						  "categoryId": "%s"
						}
						""".formatted(suffix, activeCategoryId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "INACT-%s",
						  "name": "Bad cat",
						  "unitPrice": 10,
						  "categoryId": "%s"
						}
						""".formatted(suffix, inactiveCategoryId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Category is inactive"));

		mockMvc.perform(post("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "sku": "MISS-%s",
						  "name": "Missing cat",
						  "unitPrice": 10,
						  "categoryId": "%s"
						}
						""".formatted(suffix, UUID.randomUUID())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Category not found"));

		mockMvc.perform(get("/api/v1/admin/products/" + UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isNotFound());

		// Seed inventory separately (P06 domain) — deactivate must not remove it.
		transactionTemplate.executeWithoutResult(status -> {
			Product product = productRepository.findById(UUID.fromString(productId)).orElseThrow();
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, 7));
		});

		mockMvc.perform(patch("/api/v1/admin/products/" + productId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "active": false }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/products/" + productId))
			.andExpect(status().isNotFound());

		MvcResult publicList = mockMvc.perform(get("/api/v1/products").param("q", "Admin Widget " + suffix))
			.andExpect(status().isOk())
			.andReturn();
		for (JsonNode item : objectMapper.readTree(publicList.getResponse().getContentAsString()).get("items")) {
			assertThat(item.get("id").asText()).isNotEqualTo(productId);
		}

		mockMvc.perform(get("/api/v1/admin/products")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.param("q", sku)
				.param("active", "false"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(productId))
			.andExpect(jsonPath("$.items[0].active").value(false))
			.andExpect(jsonPath("$.items[0].inStock").value(true));

		Integer quantityOnHand = transactionTemplate.execute(status ->
				inventoryRepository.findById(UUID.fromString(productId)).map(Inventory::getQuantityOnHand).orElse(-1)
		);
		assertThat(quantityOnHand).isEqualTo(7);

		mockMvc.perform(delete("/api/v1/admin/products/" + productId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isMethodNotAllowed());

		assertThat(body.has("createdAt")).isFalse();
	}

	@Test
	void openApiIncludesAdminProductPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/admin/products']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/status']").exists());
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.prod@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.prod@example.com", List.of("CUSTOMER"));
	}
}
