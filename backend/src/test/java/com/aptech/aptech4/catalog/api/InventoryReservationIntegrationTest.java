package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.application.InventoryReservationService;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryReservationIntegrationTest {

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
		registry.add("aptech.inventory.reservation-ttl-seconds", () -> "2");
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
	InventoryReservationService reservationService;

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
	void reserveReleaseConfirmIdempotencyExpiryAndOwnership() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String tokenA = register("inv.a." + suffix + "@example.com", "Inv A");
		String tokenB = register("inv.b." + suffix + "@example.com", "Inv B");
		UUID userA = userIdFromToken(tokenA);

		UUID productId = seedProduct("INV-" + suffix, 5);

		mockMvc.perform(post("/api/v1/inventory/reservations"))
			.andExpect(status().isUnauthorized());

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "a@example.com", List.of("ADMIN"));
		mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "x", "items": [{ "productId": "%s", "quantity": 1 }] }
						""".formatted(productId)))
			.andExpect(status().isForbidden());

		String key = "idem-" + suffix;
		MvcResult created = mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "idempotencyKey": "%s",
						  "items": [{ "productId": "%s", "quantity": 2 }],
						  "userId": "%s"
						}
						""".formatted(key, productId, UUID.randomUUID())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
		String reservationId = body.get("id").asText();
		assertThat(body.get("userId").asText()).isEqualTo(userA.toString());

		assertInventory(productId, 5, 2);

		MvcResult replay = mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "items": [{ "productId": "%s", "quantity": 2 }] }
						""".formatted(key, productId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(reservationId))
			.andReturn();
		assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText())
			.isEqualTo(reservationId);
		assertInventory(productId, 5, 2);

		mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "other-%s", "items": [{ "productId": "%s", "quantity": 4 }] }
						""".formatted(suffix, productId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Insufficient stock"));
		assertInventory(productId, 5, 2);

		mockMvc.perform(get("/api/v1/inventory/reservations/" + reservationId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RELEASED"));
		assertInventory(productId, 5, 0);

		mockMvc.perform(post("/api/v1/inventory/reservations/" + reservationId + "/release")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RELEASED"));
		assertInventory(productId, 5, 0);

		String key2 = "idem2-" + suffix;
		MvcResult second = mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "items": [{ "productId": "%s", "quantity": 3 }] }
						""".formatted(key2, productId)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID reservation2 = UUID.fromString(objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText());
		assertInventory(productId, 5, 3);

		asUser(userA, () -> {
			StockReservationResponse confirmed = reservationService.confirm(reservation2, userA);
			assertThat(confirmed.status()).isEqualTo("CONFIRMED");
		});
		assertInventory(productId, 2, 0);

		asUser(userA, () -> {
			StockReservationResponse again = reservationService.confirm(reservation2, userA);
			assertThat(again.status()).isEqualTo("CONFIRMED");
		});
		assertInventory(productId, 2, 0);

		asUser(userA, () -> assertThatThrownBy(() -> reservationService.confirm(UUID.fromString(reservationId), userA))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("released"));

		UUID inactiveId = transactionTemplate.execute(status -> {
			Category cat = categoryRepository.findAll().stream()
				.filter(c -> c.getSlug().startsWith("inv-cat-"))
				.findFirst()
				.orElseThrow();
			Product inactive = productRepository.saveAndFlush(Product.createNew(
					cat, "INV-X-" + suffix, "Inactive " + suffix, null, new BigDecimal("10"), "VND", false
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(inactive, 10));
			return inactive.getId();
		});
		mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "bad-%s", "items": [{ "productId": "%s", "quantity": 1 }] }
						""".formatted(suffix, inactiveId)))
			.andExpect(status().isBadRequest());

		String keyExp = "exp-" + suffix;
		MvcResult shortLived = mockMvc.perform(post("/api/v1/inventory/reservations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "%s", "items": [{ "productId": "%s", "quantity": 1 }] }
						""".formatted(keyExp, productId)))
			.andExpect(status().isCreated())
			.andReturn();
		String expId = objectMapper.readTree(shortLived.getResponse().getContentAsString()).get("id").asText();
		assertInventory(productId, 2, 1);
		Thread.sleep(2500);
		mockMvc.perform(get("/api/v1/inventory/reservations/" + expId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RELEASED"));
		assertInventory(productId, 2, 0);

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/inventory/reservations']").exists());
	}

	@Test
	void concurrentReservesDoNotOversell() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID productId = seedProduct("CONC-" + suffix, 5);

		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tokens.add(register("conc." + i + "." + suffix + "@example.com", "Conc " + i));
		}

		ExecutorService pool = Executors.newFixedThreadPool(10);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();
		List<Future<?>> futures = new ArrayList<>();

		for (int i = 0; i < 10; i++) {
			String token = tokens.get(i);
			String key = "conc-" + i + "-" + suffix;
			futures.add(pool.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				MvcResult result = mockMvc.perform(post("/api/v1/inventory/reservations")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "%s", "items": [{ "productId": "%s", "quantity": 1 }] }
								""".formatted(key, productId)))
					.andReturn();
				int status = result.getResponse().getStatus();
				if (status == 201) {
					successes.incrementAndGet();
				}
				else if (status == 409) {
					conflicts.incrementAndGet();
				}
				else {
					throw new IllegalStateException("Unexpected status " + status + ": "
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

		assertThat(successes.get()).isEqualTo(5);
		assertThat(conflicts.get()).isEqualTo(5);
		assertInventory(productId, 5, 5);
	}

	private UUID seedProduct(String sku, int onHand) {
		String slug = "inv-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Inv Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, "Product " + sku, null, new BigDecimal("10.00"), "VND", true
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

	private UUID userIdFromToken(String accessToken) {
		return jwtService.parseAccessToken(accessToken).userId();
	}

	private void asUser(UUID userId, Runnable action) {
		var principal = new JwtService.AccessTokenPrincipal(userId, "u@example.com", List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContextHolder.getContext().setAuthentication(auth);
		try {
			action.run();
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}
}
