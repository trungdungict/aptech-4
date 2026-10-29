package com.aptech.aptech4.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutIntegrationTest {

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
		registry.add("aptech.payment.mock.allow-client-force-failure", () -> "true");
		registry.add("aptech.payment.mock.default-outcome", () -> "SUCCEEDED");
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
	AddressRepository addressRepository;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void checkoutSuccessFailureIdempotencyAndOwnership() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("chk." + suffix + "@example.com", "Checkout User");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID otherAddress = seedAddress(
				jwtService.parseAccessToken(register("chk.other." + suffix + "@example.com", "Other")).userId(),
				"Other " + suffix
		);

		UUID productId = seedProduct("CHK-" + suffix, new BigDecimal("150.00"), 10);

		mockMvc.perform(post("/api/v1/checkout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "k", "addressId": "%s" }
						""".formatted(addressId)))
			.andExpect(status().isUnauthorized());

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin@example.com", List.of("ADMIN"));
		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "k", "addressId": "%s" }
						""".formatted(addressId)))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "empty-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Cart is empty"));

		addToCart(token, productId, 2);

		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "bad-addr-%s", "addressId": "%s" }
						""".formatted(suffix, otherAddress)))
			.andExpect(status().isNotFound());

		String failKey = "fail-" + suffix;
		MvcResult failed = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "%s",
						  "addressId": "%s",
						  "simulateFailure": true,
						  "paymentStatus": "SUCCEEDED",
						  "totalAmount": 1
						}
						""".formatted(failKey, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("FAILED"))
			.andExpect(jsonPath("$.payment.status").value("FAILED"))
			.andExpect(jsonPath("$.cartCleared").value(false))
			.andExpect(jsonPath("$.totalAmount").value(300.00))
			.andReturn();

		assertInventory(productId, 10, 0);

		MvcResult cartAfterFail = mockMvc.perform(get("/api/v1/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andReturn();
		assertThat(objectMapper.readTree(cartAfterFail.getResponse().getContentAsString())
			.get("items").get(0).get("quantity").asInt()).isEqualTo(2);

		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "addressId": "%s", "simulateFailure": true }
						""".formatted(failKey, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderId").value(
					objectMapper.readTree(failed.getResponse().getContentAsString()).get("orderId").asText()))
			.andExpect(jsonPath("$.orderStatus").value("FAILED"));

		String okKey = "ok-" + suffix;
		MvcResult paid = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "addressId": "%s" }
						""".formatted(okKey, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("PAID"))
			.andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.payment.provider").value("MOCK"))
			.andExpect(jsonPath("$.cartCleared").value(true))
			.andExpect(jsonPath("$.items[0].productName").value("Product CHK-" + suffix))
			.andExpect(jsonPath("$.items[0].unitPrice").value(150.00))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[0].lineTotal").value(300.00))
			.andExpect(jsonPath("$.totalAmount").value(300.00))
			.andReturn();

		JsonNode paidBody = objectMapper.readTree(paid.getResponse().getContentAsString());
		String orderId = paidBody.get("orderId").asText();
		assertInventory(productId, 8, 0);

		mockMvc.perform(get("/api/v1/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));

		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "addressId": "%s" }
						""".formatted(okKey, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderId").value(orderId))
			.andExpect(jsonPath("$.orderStatus").value("PAID"));

		addToCart(token, productId, 1);
		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "addressId": "%s", "simulateFailure": true }
						""".formatted(okKey, addressId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Idempotency key reused with different request"));

		UUID inactive = seedProduct("CHK-X-" + suffix, new BigDecimal("10.00"), 5);
		transactionTemplate.executeWithoutResult(status -> {
			Product p = productRepository.findById(inactive).orElseThrow();
			p.setActive(false);
			p.touch();
			productRepository.saveAndFlush(p);
		});
		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 1 }
						""".formatted(inactive)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/checkout']").exists());
	}

	@Test
	void concurrentCheckoutDoesNotOversell() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProduct("COC-" + suffix, new BigDecimal("20.00"), 1);

		List<String> tokens = new ArrayList<>();
		List<UUID> addressIds = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			String token = register("coc." + i + "." + suffix + "@example.com", "Coc " + i);
			tokens.add(token);
			UUID userId = jwtService.parseAccessToken(token).userId();
			addressIds.add(seedAddress(userId, "Addr " + i));
			addToCart(token, productId, 1);
		}

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger paid = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (int i = 0; i < 2; i++) {
			String token = tokens.get(i);
			UUID addressId = addressIds.get(i);
			String key = "coc-" + i + "-" + suffix;
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "%s", "addressId": "%s" }
								""".formatted(key, addressId)))
					.andReturn();
				int status = result.getResponse().getStatus();
				if (status == 201) {
					JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
					if ("PAID".equals(body.get("orderStatus").asText())) {
						paid.incrementAndGet();
					}
				}
				else if (status == 409) {
					conflicts.incrementAndGet();
				}
				else {
					throw new IllegalStateException("Unexpected " + status + " "
							+ result.getResponse().getContentAsString());
				}
				return null;
			}));
		}

		start.countDown();
		for (Future<?> future : futures) {
			future.get(30, TimeUnit.SECONDS);
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(paid.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		assertInventory(productId, 0, 0);
	}

	private void addToCart(String token, UUID productId, int qty) throws Exception {
		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": %d }
						""".formatted(productId, qty)))
			.andExpect(status().isOk());
	}

	private UUID seedProduct(String sku, BigDecimal price, int onHand) {
		String slug = "chk-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Chk Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, "Product " + sku, null, price, "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, onHand));
			return product.getId();
		});
	}

	private UUID seedAddress(UUID userId, String label) {
		return transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.findById(userId).orElseThrow();
			Address address = Address.createNew(
					user, "Recipient " + label, "Line 1", null, "Hanoi", null, "100000", "VN", "0900000000", true
			);
			address.setLabel(label);
			return addressRepository.saveAndFlush(address).getId();
		});
	}

	private void assertInventory(UUID productId, int onHand, int reserved) {
		Inventory inv = transactionTemplate.execute(status ->
				inventoryRepository.findById(productId).orElseThrow()
		);
		assertThat(inv.getQuantityOnHand()).isEqualTo(onHand);
		assertThat(inv.getQuantityReserved()).isEqualTo(reserved);
	}

	private String register(String email, String fullName) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "%s"
						}
						""".formatted(email, fullName)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}
}
