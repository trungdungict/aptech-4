package com.aptech.aptech4.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.order.domain.OrderPromotionSnapshot;
import com.aptech.aptech4.order.persistence.OrderPromotionSnapshotRepository;
import com.aptech.aptech4.promotion.application.PromotionCampaignService;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OrderPromotionGetIntegrationTest {

	private static final Instant WINDOW_START = Instant.parse("2020-01-01T00:00:00Z");
	private static final Instant WINDOW_END = Instant.parse("2099-01-01T00:00:00Z");

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
	PromotionStrategyRepository strategyRepository;

	@Autowired
	PromotionCampaignRepository campaignRepository;

	@Autowired
	PromotionCampaignService campaignService;

	@Autowired
	OrderPromotionSnapshotRepository snapshotRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void expireLeftoverActiveCampaigns() {
		transactionTemplate.executeWithoutResult(status -> {
			for (PromotionCampaign campaign : campaignRepository.findAll()) {
				if (campaign.getStatus() == PromotionCampaignStatus.ACTIVE) {
					campaign.setStatus(PromotionCampaignStatus.EXPIRED);
					campaignRepository.save(campaign);
				}
			}
		});
	}

	@Test
	void orderGet_singleAndMultipleSnapshots_matchPersistedAmounts() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("oget." + suffix + "@example.com", "Order Get");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID productId = seedProduct("OG-" + suffix, new BigDecimal("100.00"), 10);

		seedActiveCampaign(suffix + "-fixed", PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null, new BigDecimal("15.00"), false, 50);
		seedActiveCampaign(suffix + "-pct", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, true, 100);

		addToCart(token, productId, 1);
		String orderId = checkout(token, addressId, "oget-multi-" + suffix);

		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.totalAmount").value(75.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(2))
				.andExpect(jsonPath("$.appliedPromotions[0].campaignName").exists())
				.andExpect(jsonPath("$.appliedPromotions[0].strategyType").exists())
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").exists());

		assertResponseMatchesPersistedSnapshots(UUID.fromString(orderId), token);

		String other = register("oget.other." + suffix + "@example.com", "Other");
		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
				.andExpect(status().isNotFound());

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.oget@example.com", List.of("ADMIN"));
		mockMvc.perform(get("/api/v1/admin/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(2));
	}

	@Test
	void orderGet_legacyNoSnapshots_returnsZeroDiscount() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("oget.legacy." + suffix + "@example.com", "Legacy");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID productId = seedProduct("OGL-" + suffix, new BigDecimal("50.00"), 5);

		addToCart(token, productId, 2);
		String orderId = checkout(token, addressId, "oget-legacy-" + suffix);

		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(0.00))
				.andExpect(jsonPath("$.totalAmount").value(100.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(0));

		mockMvc.perform(get("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id=='%s')].discountAmount".formatted(orderId)).value(0.00));
	}

	@Test
	void orderGet_afterActiveCampaignStrategyChange_keepsHistoricalSnapshots() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("oget.strat." + suffix + "@example.com", "StratChange");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID productId = seedProduct("OGS-" + suffix, new BigDecimal("100.00"), 5);

		UUID campaignId = seedActiveCampaign(suffix + "-strat", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100);
		UUID originalStrategyId = transactionTemplate.execute(status ->
				campaignRepository.findByIdWithStrategy(campaignId).orElseThrow().getStrategy().getId()
		);

		addToCart(token, productId, 1);
		String orderId = checkout(token, addressId, "oget-strat-" + suffix);

		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(10.00))
				.andExpect(jsonPath("$.appliedPromotions[0].strategyType").value("PERCENTAGE_DISCOUNT"))
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").value(10.00));

		UUID replacementStrategyId = transactionTemplate.execute(tx ->
				strategyRepository.saveAndFlush(PromotionStrategy.createNew(
						"Replacement " + suffix,
						"R-" + suffix,
						PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
						null,
						new BigDecimal("50.00"),
						null,
						null,
						null,
						false,
						100,
						true
				)).getId()
		);

		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.strat@example.com", List.of("ADMIN"));
		PromotionCampaign campaign = transactionTemplate.execute(status ->
				campaignRepository.findByIdFully(campaignId).orElseThrow()
		);
		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/" + campaignId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "%s",
								  "slug": "%s",
								  "startsAt": "%s",
								  "endsAt": "%s",
								  "priority": 100,
								  "stackable": false,
								  "notes": null,
								  "productIds": [],
								  "categoryIds": []
								}
								""".formatted(
								replacementStrategyId,
								campaign.getName(),
								campaign.getSlug(),
								WINDOW_START,
								WINDOW_END
						)))
				.andExpect(status().isOk());

		assertThat(originalStrategyId).isNotEqualTo(replacementStrategyId);

		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(10.00))
				.andExpect(jsonPath("$.totalAmount").value(90.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1))
				.andExpect(jsonPath("$.appliedPromotions[0].strategyType").value("PERCENTAGE_DISCOUNT"))
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").value(10.00));

		transactionTemplate.executeWithoutResult(status -> {
			OrderPromotionSnapshot snapshot =
					snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(UUID.fromString(orderId)).getFirst();
			assertThat(snapshot.getStrategyType()).isEqualTo("PERCENTAGE_DISCOUNT");
			assertThat(snapshot.getDiscountPercent()).isEqualByComparingTo("10.00");
			assertThat(snapshot.getDiscountAmount()).isEqualByComparingTo("10.00");
			assertThat(snapshot.getStrategyId()).isEqualTo(originalStrategyId);
		});
	}

	@Test
	void orderGet_afterCampaignAndStrategyDelete_keepsHistoricalPricing() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("oget.hist." + suffix + "@example.com", "Hist");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID productId = seedProduct("OGH-" + suffix, new BigDecimal("200.00"), 5);

		UUID campaignId = seedActiveCampaign(suffix + "-hist", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("20.00"), null, false, 100);
		UUID strategyId = transactionTemplate.execute(status ->
				campaignRepository.findByIdWithStrategy(campaignId).orElseThrow().getStrategy().getId()
		);

		addToCart(token, productId, 1);
		String orderId = checkout(token, addressId, "oget-hist-" + suffix);

		MvcResult before = mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(40.00))
				.andExpect(jsonPath("$.totalAmount").value(160.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1))
				.andExpect(jsonPath("$.appliedPromotions[0].campaignName").value("Camp " + suffix + "-hist"))
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").value(40.00))
				.andReturn();
		JsonNode beforeBody = objectMapper.readTree(before.getResponse().getContentAsString());
		String campaignName = beforeBody.get("appliedPromotions").get(0).get("campaignName").asText();
		String strategyType = beforeBody.get("appliedPromotions").get(0).get("strategyType").asText();

		campaignService.delete(campaignId);
		transactionTemplate.executeWithoutResult(status -> strategyRepository.deleteById(strategyId));

		mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountAmount").value(40.00))
				.andExpect(jsonPath("$.totalAmount").value(160.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1))
				.andExpect(jsonPath("$.appliedPromotions[0].campaignName").value(campaignName))
				.andExpect(jsonPath("$.appliedPromotions[0].strategyType").value(strategyType))
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").value(40.00))
				.andExpect(jsonPath("$.appliedPromotions[0].campaignId").value(nullValue()));

		transactionTemplate.executeWithoutResult(status -> {
			List<OrderPromotionSnapshot> snapshots =
					snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(UUID.fromString(orderId));
			assertThat(snapshots).hasSize(1);
			assertThat(snapshots.getFirst().getCampaignId()).isNull();
			assertThat(snapshots.getFirst().getStrategyId()).isNull();
			assertThat(snapshots.getFirst().getDiscountAmount()).isEqualByComparingTo("40.00");
			assertThat(snapshots.getFirst().getCampaignName()).isEqualTo(campaignName);
		});
	}

	private void assertResponseMatchesPersistedSnapshots(UUID orderId, String token) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/orders/" + orderId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		BigDecimal responseDiscount = body.get("discountAmount").decimalValue();
		BigDecimal snapshotSum = BigDecimal.ZERO;
		for (JsonNode applied : body.get("appliedPromotions")) {
			snapshotSum = snapshotSum.add(applied.get("discountAmount").decimalValue());
		}
		assertThat(snapshotSum).isEqualByComparingTo(responseDiscount);

		transactionTemplate.executeWithoutResult(status -> {
			List<OrderPromotionSnapshot> snapshots =
					snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(orderId);
			BigDecimal dbSum = snapshots.stream()
					.map(OrderPromotionSnapshot::getDiscountAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(dbSum).isEqualByComparingTo(responseDiscount);
			assertThat(snapshots).hasSize(body.get("appliedPromotions").size());
		});
	}

	private String checkout(String token, UUID addressId, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "%s", "addressId": "%s" }
								""".formatted(key, addressId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderStatus").value("PAID"))
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();
	}

	private UUID seedActiveCampaign(
			String suffix,
			PromotionStrategyType type,
			BigDecimal percent,
			BigDecimal amount,
			boolean stackable,
			int priority
	) {
		return transactionTemplate.execute(tx -> {
			PromotionStrategy strategy = strategyRepository.saveAndFlush(PromotionStrategy.createNew(
					"Strat " + suffix,
					"C-" + suffix,
					type,
					percent,
					amount,
					null,
					null,
					null,
					stackable,
					priority,
					true
			));
			PromotionCampaign campaign = PromotionCampaign.createNew(
					strategy,
					"Camp " + suffix,
					"camp-" + suffix,
					WINDOW_START,
					WINDOW_END,
					null
			);
			campaign.setStatus(PromotionCampaignStatus.ACTIVE);
			campaign.setPriority(priority);
			campaign.setStackable(stackable);
			return campaignRepository.saveAndFlush(campaign).getId();
		});
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
		String slug = "oget-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"OGet Cat " + sku, slug, null, null
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
