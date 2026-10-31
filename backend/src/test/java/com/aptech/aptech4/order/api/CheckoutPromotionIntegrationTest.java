package com.aptech.aptech4.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.OrderPromotionSnapshotRepository;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
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
class CheckoutPromotionIntegrationTest {

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
	ShopOrderRepository shopOrderRepository;

	@Autowired
	OrderPromotionSnapshotRepository snapshotRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void expireLeftoverActiveCampaigns() {
		// Shared IT DB can retain ACTIVE campaigns from prior suites; isolate pricing.
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
	void noPromotion_preservesLegacyTotalAndZeroDiscount() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);
		addToCart(fx.token(), fx.productId(), 2);

		MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "nopromo-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderStatus").value("PAID"))
				.andExpect(jsonPath("$.subtotalAmount").value(200.00))
				.andExpect(jsonPath("$.discountAmount").value(0.00))
				.andExpect(jsonPath("$.totalAmount").value(200.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(0))
				.andExpect(jsonPath("$.payment.amount").value(200.00))
				.andReturn();

		UUID orderId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
				.get("orderId").asText());
		assertPersistedDiscount(orderId, "0.00", 0);
	}

	@Test
	void fixedAndPercentagePromotions_applyAndPersistSnapshots() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);

		seedActiveCampaign(suffix + "-fixed", PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null, new BigDecimal("15.00"), false, 50);
		seedActiveCampaign(suffix + "-pct", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, true, 100);

		addToCart(fx.token(), fx.productId(), 1);

		MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "mix-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderStatus").value("PAID"))
				.andExpect(jsonPath("$.subtotalAmount").value(100.00))
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.totalAmount").value(75.00))
				.andExpect(jsonPath("$.payment.amount").value(75.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(2))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID orderId = UUID.fromString(body.get("orderId").asText());
		assertPersistedDiscount(orderId, "25.00", 2);
		assertSnapshotSumEqualsOrderDiscount(orderId);
	}

	@Test
	void twoNonStackable_appliesHigherPriorityOnly() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("200.00"), 5);

		seedActiveCampaign(suffix + "-low", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 50);
		seedActiveCampaign(suffix + "-high", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("20.00"), null, false, 200);

		addToCart(fx.token(), fx.productId(), 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "prio-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.discountAmount").value(40.00))
				.andExpect(jsonPath("$.totalAmount").value(160.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1))
				.andExpect(jsonPath("$.appliedPromotions[0].discountAmount").value(40.00));
	}

	@Test
	void productEligibility_ignoresNonMatchingLines() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("50.00"), 10);
		UUID otherProduct = seedProduct("OTH-" + suffix, new BigDecimal("80.00"), 10, null);

		UUID campaignId = seedActiveCampaign(suffix + "-prod", PromotionStrategyType.PRODUCT_DISCOUNT,
				new BigDecimal("50.00"), null, false, 100);
		transactionTemplate.executeWithoutResult(status -> {
			PromotionCampaign campaign = campaignRepository.findByIdFully(campaignId).orElseThrow();
			campaign.replaceProductIds(Set.of(fx.productId()));
			campaignRepository.saveAndFlush(campaign);
		});

		addToCart(fx.token(), fx.productId(), 1);
		addToCart(fx.token(), otherProduct, 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "elig-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subtotalAmount").value(130.00))
				.andExpect(jsonPath("$.discountAmount").value(25.00))
				.andExpect(jsonPath("$.totalAmount").value(105.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1));
	}

	@Test
	void inactivePausedNotStartedExpired_doNotDiscount() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);

		seedCampaign(suffix + "-paused", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100,
				PromotionCampaignStatus.PAUSED, WINDOW_START, WINDOW_END);
		seedCampaign(suffix + "-future", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100,
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2090-01-01T00:00:00Z"),
				Instant.parse("2095-01-01T00:00:00Z"));
		seedCampaign(suffix + "-past", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100,
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2010-01-01T00:00:00Z"),
				Instant.parse("2011-01-01T00:00:00Z"));

		UUID inactiveStratCampaign = seedActiveCampaign(suffix + "-instrat", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100);
		transactionTemplate.executeWithoutResult(status -> {
			PromotionCampaign campaign = campaignRepository.findByIdWithStrategy(inactiveStratCampaign).orElseThrow();
			campaign.getStrategy().setActive(false);
			strategyRepository.saveAndFlush(campaign.getStrategy());
		});

		addToCart(fx.token(), fx.productId(), 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "dead-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.discountAmount").value(0.00))
				.andExpect(jsonPath("$.totalAmount").value(100.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(0));
	}

	@Test
	void paymentFailure_persistsOrderAndSnapshotsAtomically() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);
		seedActiveCampaign(suffix + "-failpct", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100);
		addToCart(fx.token(), fx.productId(), 1);

		MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "idempotencyKey": "failpromo-%s",
								  "addressId": "%s",
								  "simulateFailure": true
								}
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderStatus").value("FAILED"))
				.andExpect(jsonPath("$.discountAmount").value(10.00))
				.andExpect(jsonPath("$.totalAmount").value(90.00))
				.andExpect(jsonPath("$.payment.amount").value(90.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1))
				.andExpect(jsonPath("$.cartCleared").value(false))
				.andReturn();

		UUID orderId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
				.get("orderId").asText());
		assertPersistedDiscount(orderId, "10.00", 1);
		assertSnapshotSumEqualsOrderDiscount(orderId);
		assertInventory(fx.productId(), 5, 0);
	}

	@Test
	void clientPreferCampaignIdsIgnored_andRoundingCap() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("33.33"), 5);
		seedActiveCampaign(suffix + "-round", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, false, 100);
		addToCart(fx.token(), fx.productId(), 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "idempotencyKey": "round-%s",
								  "addressId": "%s",
								  "preferCampaignIds": ["00000000-0000-0000-0000-000000000001"],
								  "discountAmount": 999,
								  "totalAmount": 1
								}
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.discountAmount").value(3.33))
				.andExpect(jsonPath("$.totalAmount").value(30.00));
	}

	@Test
	void categoryEligibility_onlyMatchingLinesDiscounted() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("40.00"), 10);
		UUID otherProduct = seedProduct("OTH-CAT-" + suffix, new BigDecimal("60.00"), 10, null);

		UUID campaignId = seedActiveCampaign(suffix + "-cat", PromotionStrategyType.CATEGORY_DISCOUNT,
				null, new BigDecimal("10.00"), false, 100);
		transactionTemplate.executeWithoutResult(status -> {
			Product product = productRepository.findById(fx.productId()).orElseThrow();
			PromotionCampaign campaign = campaignRepository.findByIdFully(campaignId).orElseThrow();
			campaign.replaceCategoryIds(Set.of(product.getCategory().getId()));
			campaignRepository.saveAndFlush(campaign);
		});

		addToCart(fx.token(), fx.productId(), 1);
		addToCart(fx.token(), otherProduct, 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "cat-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.subtotalAmount").value(100.00))
				.andExpect(jsonPath("$.discountAmount").value(10.00))
				.andExpect(jsonPath("$.totalAmount").value(90.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(1));
	}

	@Test
	void stackableEndTrim_linePricesUnchanged_andPromotionSummaryPersisted() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);

		seedActiveCampaign(suffix + "-big-a", PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null, new BigDecimal("80.00"), true, 100);
		seedActiveCampaign(suffix + "-big-b", PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null, new BigDecimal("80.00"), true, 90);

		addToCart(fx.token(), fx.productId(), 1);

		MvcResult result = mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "trim-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.orderStatus").value("PAID"))
				.andExpect(jsonPath("$.subtotalAmount").value(100.00))
				.andExpect(jsonPath("$.discountAmount").value(100.00))
				.andExpect(jsonPath("$.totalAmount").value(0.00))
				.andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
				.andExpect(jsonPath("$.items[0].lineTotal").value(100.00))
				.andExpect(jsonPath("$.payment.amount").value(0.00))
				.andReturn();

		UUID orderId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
				.get("orderId").asText());
		assertSnapshotSumEqualsOrderDiscount(orderId);
		assertInventory(fx.productId(), 4, 0);

		transactionTemplate.executeWithoutResult(status -> {
			ShopOrder order = shopOrderRepository.findByIdWithItems(orderId).orElseThrow();
			assertThat(order.getPromotionSummary()).isNotBlank();
			assertThat(order.getItems()).hasSize(1);
			assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("100.00");
			assertThat(order.getItems().getFirst().getLineTotal()).isEqualByComparingTo("100.00");
			BigDecimal snapSum = snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(orderId).stream()
					.map(OrderPromotionSnapshot::getDiscountAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(snapSum).isEqualByComparingTo("100.00");
		});
	}

	@Test
	void twoStackable_bothAppliedNonCompounding_atCheckout() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Fixture fx = fixture(suffix, new BigDecimal("100.00"), 5);

		seedActiveCampaign(suffix + "-sa", PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"), null, true, 100);
		seedActiveCampaign(suffix + "-sb", PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null, new BigDecimal("5.00"), true, 90);

		addToCart(fx.token(), fx.productId(), 1);

		mockMvc.perform(post("/api/v1/checkout")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + fx.token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "idempotencyKey": "stack-%s", "addressId": "%s" }
								""".formatted(suffix, fx.addressId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.discountAmount").value(15.00))
				.andExpect(jsonPath("$.totalAmount").value(85.00))
				.andExpect(jsonPath("$.appliedPromotions.length()").value(2));
	}

	private void assertPersistedDiscount(UUID orderId, String expectedDiscount, int snapshotCount) {
		transactionTemplate.executeWithoutResult(status -> {
			ShopOrder order = shopOrderRepository.findById(orderId).orElseThrow();
			assertThat(order.getDiscountAmount()).isEqualByComparingTo(expectedDiscount);
			List<OrderPromotionSnapshot> snapshots = snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(orderId);
			assertThat(snapshots).hasSize(snapshotCount);
		});
	}

	private void assertSnapshotSumEqualsOrderDiscount(UUID orderId) {
		transactionTemplate.executeWithoutResult(status -> {
			ShopOrder order = shopOrderRepository.findById(orderId).orElseThrow();
			BigDecimal sum = snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(orderId).stream()
					.map(OrderPromotionSnapshot::getDiscountAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			assertThat(sum).isEqualByComparingTo(order.getDiscountAmount());
		});
	}

	private UUID seedActiveCampaign(
			String suffix,
			PromotionStrategyType type,
			BigDecimal percent,
			BigDecimal amount,
			boolean stackable,
			int priority
	) {
		return seedCampaign(suffix, type, percent, amount, stackable, priority,
				PromotionCampaignStatus.ACTIVE, WINDOW_START, WINDOW_END);
	}

	private UUID seedCampaign(
			String suffix,
			PromotionStrategyType type,
			BigDecimal percent,
			BigDecimal amount,
			boolean stackable,
			int priority,
			PromotionCampaignStatus status,
			Instant startsAt,
			Instant endsAt
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
					startsAt,
					endsAt,
					null
			);
			campaign.setStatus(status);
			campaign.setPriority(priority);
			campaign.setStackable(stackable);
			return campaignRepository.saveAndFlush(campaign).getId();
		});
	}

	private Fixture fixture(String suffix, BigDecimal price, int onHand) throws Exception {
		String token = register("promo." + suffix + "@example.com", "Promo User");
		UUID userId = jwtService.parseAccessToken(token).userId();
		UUID addressId = seedAddress(userId, "Home " + suffix);
		UUID productId = seedProduct("PR-" + suffix, price, onHand, null);
		return new Fixture(token, addressId, productId);
	}

	private record Fixture(String token, UUID addressId, UUID productId) {
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

	private UUID seedProduct(String sku, BigDecimal price, int onHand, UUID categoryId) {
		String slug = "promo-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category;
			if (categoryId != null) {
				category = categoryRepository.findById(categoryId).orElseThrow();
			}
			else {
				category = categoryRepository.saveAndFlush(Category.createNew(
						"Promo Cat " + sku, slug, null, null
				));
			}
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
