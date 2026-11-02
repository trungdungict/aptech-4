package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
class AdminInventoryListIntegrationTest {

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
		registry.add("aptech.inventory.low-stock-threshold", () -> "5");
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
	void listAndSummary_requireAdmin() throws Exception {
		String customer = customerToken();
		mockMvc.perform(get("/api/v1/admin/inventory"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/inventory/summary"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/inventory")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/admin/inventory/summary")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isForbidden());
	}

	@Test
	void listFiltersPaginationSortingAndMissingInventoryRow() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		UUID inStockId = seedProductWithInventory("INV-OK-" + suffix, "Alpha Plenty " + suffix, true, 20);
		UUID lowId = seedProductWithInventory("INV-LOW-" + suffix, "Beta Low " + suffix, true, 3);
		UUID outId = seedProductWithInventory("INV-OUT-" + suffix, "Gamma Out " + suffix, true, 0);
		UUID missingId = seedProductWithoutInventory("INV-MISS-" + suffix, "Delta Missing " + suffix, true);
		UUID inactiveLowId = seedProductWithInventory("INV-INA-" + suffix, "Epsilon Inactive " + suffix, false, 2);

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("sort", "name,asc")
						.param("page", "0")
						.param("size", "10")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.items[0].name").value("Alpha Plenty " + suffix))
				.andExpect(jsonPath("$.items[0].available").value(20))
				.andExpect(jsonPath("$.items[0].hasInventoryRow").value(true));

		MvcResult missingPage = mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", "INV-MISS-" + suffix)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(missingId.toString()))
				.andExpect(jsonPath("$.items[0].hasInventoryRow").value(false))
				.andExpect(jsonPath("$.items[0].quantityOnHand").value(0))
				.andExpect(jsonPath("$.items[0].quantityReserved").value(0))
				.andExpect(jsonPath("$.items[0].available").value(0))
				.andReturn();
		assertThat(objectMapper.readTree(missingPage.getResponse().getContentAsString())
				.get("items").get(0).get("updatedAt").isNull()).isTrue();

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("stockStatus", "LOW")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("stockStatus", "OUT")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("stockStatus", "IN_STOCK")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(inStockId.toString()));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("active", "false")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(inactiveLowId.toString()))
				.andExpect(jsonPath("$.items[0].active").value(false));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", suffix)
						.param("page", "0")
						.param("size", "2")
						.param("sort", "name,asc")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.first").value(true))
				.andExpect(jsonPath("$.last").value(false));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("sort", "hacked,asc")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest());

		// boundary: available == threshold is LOW, available == threshold+1 is IN_STOCK
		assertThat(lowId).isNotNull();
		assertThat(outId).isNotNull();
		UUID boundaryLow = seedProductWithInventory("INV-B5-" + suffix, "Boundary Five " + suffix, true, 5);
		UUID boundaryIn = seedProductWithInventory("INV-B6-" + suffix, "Boundary Six " + suffix, true, 6);

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", "INV-B5-" + suffix)
						.param("stockStatus", "LOW")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(boundaryLow.toString()))
				.andExpect(jsonPath("$.items[0].available").value(5));

		mockMvc.perform(get("/api/v1/admin/inventory")
						.param("q", "INV-B6-" + suffix)
						.param("stockStatus", "IN_STOCK")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].productId").value(boundaryIn.toString()));
	}

	@Test
	void summaryMatchesListCountsForStockBuckets() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		seedProductWithInventory("SUM-OK-" + suffix, "Sum Ok " + suffix, true, 10);
		seedProductWithInventory("SUM-LOW-" + suffix, "Sum Low " + suffix, true, 2);
		seedProductWithInventory("SUM-OUT-" + suffix, "Sum Out " + suffix, true, 0);
		seedProductWithoutInventory("SUM-MISS-" + suffix, "Sum Miss " + suffix, true);

		MvcResult summaryResult = mockMvc.perform(get("/api/v1/admin/inventory/summary")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lowStockThreshold").value(5))
				.andExpect(jsonPath("$.availableDefinition").isString())
				.andReturn();

		JsonNode summary = objectMapper.readTree(summaryResult.getResponse().getContentAsString());
		long total = summary.get("totalProducts").asLong();
		long inStock = summary.get("inStockCount").asLong();
		long low = summary.get("lowStockCount").asLong();
		long out = summary.get("outOfStockCount").asLong();
		assertThat(inStock + low + out).isEqualTo(total);

		long listAll = totalElements(admin, null);
		assertThat(listAll).isEqualTo(total);
		assertThat(totalElements(admin, "IN_STOCK")).isEqualTo(inStock);
		assertThat(totalElements(admin, "LOW")).isEqualTo(low);
		assertThat(totalElements(admin, "OUT")).isEqualTo(out);
	}

	@Test
	void absolutePutStillRejectsBelowReserved_andOpenApiListsInventoryPaths() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProductWithInventory("PUT-OK-" + suffix, "Put Product " + suffix, true, 5);

		transactionTemplate.executeWithoutResult(status -> {
			Inventory inv = inventoryRepository.findById(productId).orElseThrow();
			inv.setQuantityReserved(3);
			inventoryRepository.saveAndFlush(inv);
		});

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "quantityOnHand": 2 }
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "quantityOnHand": 4 }
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantityOnHand").value(4))
				.andExpect(jsonPath("$.quantityReserved").value(3))
				.andExpect(jsonPath("$.available").value(1));

		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/admin/inventory']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/inventory/summary']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/inventory']").exists());
	}

	private long totalElements(String adminToken, String stockStatus) throws Exception {
		var request = get("/api/v1/admin/inventory").param("size", "1");
		if (stockStatus != null) {
			request = request.param("stockStatus", stockStatus);
		}
		MvcResult result = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("totalElements").asLong();
	}

	private UUID seedProductWithoutInventory(String sku, String name, boolean active) {
		return transactionTemplate.execute(status -> {
			String slug = "adm-inv-list-" + UUID.randomUUID().toString().substring(0, 8);
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Inv List Cat " + sku, slug, null, null
			));
			Product product = Product.createNew(
					category, sku, name, null, new BigDecimal("15.00"), "VND", active
			);
			return productRepository.saveAndFlush(product).getId();
		});
	}

	private UUID seedProductWithInventory(String sku, String name, boolean active, int onHand) {
		return transactionTemplate.execute(status -> {
			String slug = "adm-inv-list-" + UUID.randomUUID().toString().substring(0, 8);
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Inv List Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, name, null, new BigDecimal("15.00"), "VND", active
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, onHand));
			return product.getId();
		});
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.invlist@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.invlist@example.com", List.of("CUSTOMER"));
	}
}
