package com.aptech.aptech4.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OrderLifecycleIntegrationTest {

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
	ShopOrderRepository shopOrderRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void customerAndAdminOrderLifecycle() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String tokenA = register("ord.a." + suffix + "@example.com", "Order A");
		String tokenB = register("ord.b." + suffix + "@example.com", "Order B");
		UUID userA = jwtService.parseAccessToken(tokenA).userId();
		UUID addressA = seedAddress(userA, "A-" + suffix);
		UUID productId = seedProduct("ORD-" + suffix, new BigDecimal("80.00"), 20);
		String originalName = "Product ORD-" + suffix;

		mockMvc.perform(get("/api/v1/orders"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/orders"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isForbidden());

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.ord@example.com", List.of("ADMIN"));

		addToCart(tokenA, productId, 1);
		MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "life-%s", "addressId": "%s" }
						""".formatted(suffix, addressA)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("PAID"))
			.andReturn();
		String orderId = objectMapper.readTree(checkout.getResponse().getContentAsString()).get("orderId").asText();

		transactionTemplate.executeWithoutResult(status -> {
			Product p = productRepository.findById(productId).orElseThrow();
			p.setName("Renamed After Checkout " + suffix);
			p.setUnitPrice(new BigDecimal("999.00"));
			p.touch();
			productRepository.saveAndFlush(p);
		});

		mockMvc.perform(get("/api/v1/orders/" + orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(orderId))
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.items[0].productName").value(originalName))
			.andExpect(jsonPath("$.items[0].unitPrice").value(80.00))
			.andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.payment.password").doesNotExist());

		mockMvc.perform(get("/api/v1/orders/" + orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.param("page", "0")
				.param("size", "10")
				.param("sort", "placedAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(orderId)).isNotEmpty())
			.andExpect(jsonPath("$.page").value(0));

		mockMvc.perform(get("/api/v1/admin/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.param("status", "PAID"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(orderId)).isNotEmpty());

		mockMvc.perform(get("/api/v1/admin/orders/" + orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(userA.toString()));

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "PROCESSING",
						  "totalAmount": 1,
						  "userId": "%s",
						  "paymentStatus": "FAILED"
						}
						""".formatted(UUID.randomUUID())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROCESSING"))
			.andExpect(jsonPath("$.totalAmount").value(80.00))
			.andExpect(jsonPath("$.userId").value(userA.toString()))
			.andExpect(jsonPath("$.payment.status").value("SUCCEEDED"));

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "COMPLETED" }
						"""))
			.andExpect(status().isConflict());

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "SHIPPED" }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHIPPED"));

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "COMPLETED" }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "PROCESSING" }
						"""))
			.andExpect(status().isConflict());

		UUID pendingId = transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.findById(userA).orElseThrow();
			Address address = addressRepository.findById(addressA).orElseThrow();
			ShopOrder pending = ShopOrder.createPending(user, address);
			pending.setCurrency("VND");
			pending.setSubtotalAmount(BigDecimal.ZERO.setScale(2));
			pending.setTotalAmount(BigDecimal.ZERO.setScale(2));
			return shopOrderRepository.saveAndFlush(pending).getId();
		});

		mockMvc.perform(post("/api/v1/orders/" + pendingId + "/cancel")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/orders']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/orders']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/orders/{orderId}/status']").exists());
	}

	@Test
	void concurrentAdminStatusUpdatesDoNotDiverge() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("ord.conc." + suffix + "@example.com", "Conc");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "C-" + suffix);
		UUID productId = seedProduct("ORC-" + suffix, new BigDecimal("10.00"), 5);
		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.conc@example.com", List.of("ADMIN"));

		addToCart(token, productId, 1);
		MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "conc-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andReturn();
		String orderId = objectMapper.readTree(checkout.getResponse().getContentAsString()).get("orderId").asText();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger success = new AtomicInteger();
		AtomicInteger conflict = new AtomicInteger();
		List<Future<?>> futures = List.of(
				pool.submit(() -> patchStatus(admin, orderId, "PROCESSING", start, success, conflict)),
				pool.submit(() -> patchStatus(admin, orderId, "CANCELLED", start, success, conflict))
		);
		start.countDown();
		for (Future<?> future : futures) {
			future.get(20, TimeUnit.SECONDS);
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(success.get() + conflict.get()).isEqualTo(2);
		assertThat(success.get()).isGreaterThanOrEqualTo(1);

		MvcResult detail = mockMvc.perform(get("/api/v1/admin/orders/" + orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andReturn();
		String status = objectMapper.readTree(detail.getResponse().getContentAsString()).get("status").asText();
		assertThat(status).isIn("PROCESSING", "CANCELLED");
		assertThat(OrderStatus.valueOf(status)).isIn(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
		System.out.printf(
				"CONCURRENT_STATUS_EVIDENCE success=%d conflict=%d finalStatus=%s%n",
				success.get(),
				conflict.get(),
				status
		);
	}

	private Void patchStatus(
			String admin,
			String orderId,
			String status,
			CountDownLatch start,
			AtomicInteger success,
			AtomicInteger conflict
	) throws Exception {
		start.await(5, TimeUnit.SECONDS);
		MvcResult result = mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "%s" }
						""".formatted(status)))
			.andReturn();
		int code = result.getResponse().getStatus();
		if (code == 200) {
			success.incrementAndGet();
		}
		else if (code == 409) {
			conflict.incrementAndGet();
		}
		else {
			throw new IllegalStateException("Unexpected " + code + " " + result.getResponse().getContentAsString());
		}
		return null;
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
		String slug = "ord-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Ord Cat " + sku, slug, null, null
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
