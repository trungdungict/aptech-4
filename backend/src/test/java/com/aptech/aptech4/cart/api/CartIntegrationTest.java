package com.aptech.aptech4.cart.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CartIntegrationTest {

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
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
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
	void cartOwnershipPricingMergeAndNoInventoryMutation() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String emailA = "cart.a." + suffix + "@example.com";
		String emailB = "cart.b." + suffix + "@example.com";

		String tokenA = register(emailA, "Cart User A");
		String tokenB = register(emailB, "Cart User B");
		String adminToken = jwtService.createAccessToken(UUID.randomUUID(), "admin.cart@example.com", List.of("ADMIN"));

		UUID productId = transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Cart Cat " + suffix, "cart-cat-" + suffix, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, "CART-" + suffix, "Cart Product " + suffix, "desc",
					new BigDecimal("100.00"), "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, 42));
			return product.getId();
		});

		mockMvc.perform(get("/api/v1/cart"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray())
			.andExpect(jsonPath("$.subtotal").value(0.0))
			.andExpect(jsonPath("$.hasUnavailableItems").value(false));

		MvcResult added = mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "productId": "%s",
						  "quantity": 2,
						  "unitPrice": 1.00,
						  "userId": "%s"
						}
						""".formatted(productId, UUID.randomUUID())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].quantity").value(2))
			.andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
			.andExpect(jsonPath("$.items[0].lineSubtotal").value(200.00))
			.andExpect(jsonPath("$.subtotal").value(200.00))
			.andExpect(jsonPath("$.items[0].available").value(true))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andReturn();

		JsonNode cartA = objectMapper.readTree(added.getResponse().getContentAsString());
		String itemId = cartA.get("items").get(0).get("id").asText();

		Integer stockAfterAdd = transactionTemplate.execute(status ->
				inventoryRepository.findById(productId).map(Inventory::getQuantityOnHand).orElse(-1)
		);
		assertThat(stockAfterAdd).isEqualTo(42);

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 3 }
						""".formatted(productId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].quantity").value(5))
			.andExpect(jsonPath("$.subtotal").value(500.00));

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantity": 1 }
						"""))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantity": 1 }
						"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantity": 4 }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity").value(4))
			.andExpect(jsonPath("$.subtotal").value(400.00));

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 0 }
						""".formatted(productId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": -1 }
						""".formatted(productId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 100 }
						""".formatted(productId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 1 }
						""".formatted(UUID.randomUUID())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Product not available"));

		UUID inactiveProductId = transactionTemplate.execute(status -> {
			Category category = categoryRepository.findAll().stream()
				.filter(c -> c.getSlug().equals("cart-cat-" + suffix))
				.findFirst()
				.orElseThrow();
			Product inactive = productRepository.saveAndFlush(Product.createNew(
					category, "CART-X-" + suffix, "Inactive Cart Product " + suffix, null,
					new BigDecimal("50.00"), "VND", false
			));
			return inactive.getId();
		});

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 1 }
						""".formatted(inactiveProductId)))
			.andExpect(status().isBadRequest());

		transactionTemplate.executeWithoutResult(status -> {
			Product product = productRepository.findById(productId).orElseThrow();
			product.setActive(false);
			product.touch();
			productRepository.saveAndFlush(product);
		});

		MvcResult afterDeactivate = mockMvc.perform(get("/api/v1/cart")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasUnavailableItems").value(true))
			.andExpect(jsonPath("$.items[0].available").value(false))
			.andExpect(jsonPath("$.items[0].productActive").value(false))
			.andReturn();
		assertThat(objectMapper.readTree(afterDeactivate.getResponse().getContentAsString())
			.get("items").get(0).get("unitPrice").decimalValue())
			.isEqualByComparingTo("100.00");

		mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "quantity": 1 }
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(delete("/api/v1/cart/items/" + itemId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.subtotal").value(0.0));

		transactionTemplate.executeWithoutResult(status -> {
			Product product = productRepository.findById(productId).orElseThrow();
			product.setActive(true);
			product.touch();
			productRepository.saveAndFlush(product);
		});

		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": 1 }
						""".formatted(productId)))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));

		Integer stockFinal = transactionTemplate.execute(status ->
				inventoryRepository.findById(productId).map(Inventory::getQuantityOnHand).orElse(-1)
		);
		assertThat(stockFinal).isEqualTo(42);
	}

	@Test
	void openApiIncludesCartPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/cart']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/cart/items']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/cart/items/{itemId}']").exists());
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
