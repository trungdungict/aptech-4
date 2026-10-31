package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
class AdminProductInventoryIntegrationTest {

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
	void adminCanViewAndUpdateInventory_customerAndAnonymousForbidden() throws Exception {
		String admin = adminToken();
		String customer = customerToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProductWithoutInventory("ADM-INV-" + suffix);

		mockMvc.perform(get("/api/v1/admin/products/" + productId + "/inventory"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 10 }
						"""))
			.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 10 }
						"""))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productId").value(productId.toString()))
			.andExpect(jsonPath("$.quantityOnHand").value(0))
			.andExpect(jsonPath("$.quantityReserved").value(0))
			.andExpect(jsonPath("$.available").value(0));

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 12 }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantityOnHand").value(12))
			.andExpect(jsonPath("$.quantityReserved").value(0))
			.andExpect(jsonPath("$.available").value(12));

		assertInventory(productId, 12, 0);

		mockMvc.perform(get("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantityOnHand").value(12))
			.andExpect(jsonPath("$.available").value(12));
	}

	@Test
	void rejectsNegativeInvalidAndBelowReservedQuantities() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProductWithInventory("ADM-INV-BAD-" + suffix, 10);

		transactionTemplate.executeWithoutResult(status -> {
			Inventory inv = inventoryRepository.findById(productId).orElseThrow();
			inv.setQuantityReserved(4);
			inventoryRepository.saveAndFlush(inv);
		});

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": -1 }
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": null }
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{}
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 3 }
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("quantityReserved"))); // reserved invariant

		assertInventory(productId, 10, 4);

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 4 }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantityOnHand").value(4))
			.andExpect(jsonPath("$.quantityReserved").value(4))
			.andExpect(jsonPath("$.available").value(0));

		mockMvc.perform(get("/api/v1/admin/products/" + UUID.randomUUID() + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isNotFound());
	}

	@Test
	void concurrentAdminUpdatesCannotProduceNegativeOrBreakReservedInvariant() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProductWithInventory("ADM-INV-RACE-" + suffix, 20);

		transactionTemplate.executeWithoutResult(status -> {
			Inventory inv = inventoryRepository.findById(productId).orElseThrow();
			inv.setQuantityReserved(5);
			inventoryRepository.saveAndFlush(inv);
		});

		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger ok = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			final int onHand = (i % 2 == 0) ? 5 : 2; // 2 is below reserved → must fail
			futures.add(pool.submit(() -> {
				start.await();
				MvcResult result = mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "quantityOnHand": %d }
								""".formatted(onHand)))
					.andReturn();
				int status = result.getResponse().getStatus();
				if (status == 200) {
					ok.incrementAndGet();
				}
				else if (status == 400) {
					rejected.incrementAndGet();
				}
				return null;
			}));
		}

		start.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(ok.get()).isGreaterThan(0);
		assertThat(rejected.get()).isGreaterThan(0);

		Inventory finalInv = transactionTemplate.execute(status ->
				inventoryRepository.findById(productId).orElseThrow()
		);
		assertThat(finalInv.getQuantityOnHand()).isGreaterThanOrEqualTo(finalInv.getQuantityReserved());
		assertThat(finalInv.getQuantityOnHand()).isGreaterThanOrEqualTo(0);
		assertThat(finalInv.getAvailable()).isGreaterThanOrEqualTo(0);
		assertThat(finalInv.getQuantityReserved()).isEqualTo(5);
	}

	@Test
	void checkoutReservationReflectsAdminStockUpdates() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProductWithoutInventory("ADM-INV-CHK-" + suffix);

		String customerToken = registerCustomer("inv.chk." + suffix + "@example.com");

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 0 }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(0));

		mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "zero-%s",
						  "items": [{ "productId": "%s", "quantity": 1 }]
						}
						""".formatted(suffix, productId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Insufficient stock"));

		mockMvc.perform(put("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantityOnHand": 3 }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(3));

		mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "ok-%s",
						  "items": [{ "productId": "%s", "quantity": 2 }]
						}
						""".formatted(suffix, productId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		assertInventory(productId, 3, 2);

		mockMvc.perform(get("/api/v1/admin/products/" + productId + "/inventory")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.quantityOnHand").value(3))
			.andExpect(jsonPath("$.quantityReserved").value(2))
			.andExpect(jsonPath("$.available").value(1));
	}

	@Test
	void openApiIncludesAdminInventoryPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/inventory']").exists());
	}

	private UUID seedProductWithoutInventory(String sku) {
		return transactionTemplate.execute(status -> {
			String slug = "adm-inv-" + UUID.randomUUID().toString().substring(0, 8);
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Admin Inv Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, "Admin Inv " + sku, null, new BigDecimal("15.00"), "VND", true
			));
			return product.getId();
		});
	}

	private UUID seedProductWithInventory(String sku, int onHand) {
		return transactionTemplate.execute(status -> {
			String slug = "adm-inv-" + UUID.randomUUID().toString().substring(0, 8);
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Admin Inv Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, "Admin Inv " + sku, null, new BigDecimal("15.00"), "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, onHand));
			return product.getId();
		});
	}

	private void assertInventory(UUID productId, int onHand, int reserved) {
		Inventory inv = transactionTemplate.execute(status ->
				inventoryRepository.findById(productId).orElseThrow()
		);
		assertThat(inv.getQuantityOnHand()).isEqualTo(onHand);
		assertThat(inv.getQuantityReserved()).isEqualTo(reserved);
		assertThat(inv.getAvailable()).isEqualTo(onHand - reserved);
	}

	private String registerCustomer(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "Inv Customer"
						}
						""".formatted(email)))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return body.get("accessToken").asText();
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.inv@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.inv@example.com", List.of("CUSTOMER"));
	}
}
