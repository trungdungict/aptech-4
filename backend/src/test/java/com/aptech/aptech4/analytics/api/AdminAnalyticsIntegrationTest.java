package com.aptech.aptech4.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
import com.aptech.aptech4.order.domain.OrderItem;
import com.aptech.aptech4.order.domain.OrderPromotionSnapshot;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
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
class AdminAnalyticsIntegrationTest {

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
	UserAccountRepository userAccountRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	ShopOrderRepository shopOrderRepository;

	@Autowired
	PromotionStrategyRepository strategyRepository;

	@Autowired
	PromotionCampaignRepository campaignRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void allAnalyticsEndpoints_requireAdmin() throws Exception {
		String customer = customerToken();
		String[] paths = {
				"/api/v1/admin/analytics/overview",
				"/api/v1/admin/analytics/orders/status",
				"/api/v1/admin/analytics/orders/timeseries",
				"/api/v1/admin/analytics/products/top",
				"/api/v1/admin/analytics/categories/sales",
				"/api/v1/admin/analytics/promotions/summary"
		};
		for (String path : paths) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void defaultWindowAndValidation() throws Exception {
		String admin = adminToken();
		Window window = uniqueWindow(4);

		MvcResult defaultResult = mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from").exists())
				.andExpect(jsonPath("$.to").exists())
				.andExpect(jsonPath("$.timezone").value("UTC"))
				.andReturn();
		JsonNode body = objectMapper.readTree(defaultResult.getResponse().getContentAsString());
		Instant from = Instant.parse(body.get("from").asText());
		Instant to = Instant.parse(body.get("to").asText());
		assertThat(ChronoUnit.DAYS.between(from, to)).isEqualTo(30);

		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", window.to().toString())
						.param("to", window.from().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("from must be before to"));

		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", "2090-01-01T00:00:00Z")
						.param("to", "2092-01-01T00:00:00Z")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Date range must be at most 366 days"));

		mockMvc.perform(get("/api/v1/admin/analytics/products/top")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.param("limit", "21")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("limit must be between 1 and 20"));

		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", "not-an-instant")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest());
	}

	@Test
	void emptyWindow_returnsZerosWithoutFabrication() throws Exception {
		String admin = adminToken();
		Window empty = uniqueWindow(9);
		LocalDate firstDay = empty.from().atZone(ZoneOffset.UTC).toLocalDate();

		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", empty.from().toString())
						.param("to", empty.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.placedOrderCount").value(0))
				.andExpect(jsonPath("$.revenueOrderCount").value(0))
				.andExpect(jsonPath("$.revenueTotal").value(0))
				.andExpect(jsonPath("$.averageOrderValue").value(0))
				.andExpect(jsonPath("$.empty").value(true))
				.andExpect(jsonPath("$.statusCounts.PAID").value(0))
				.andExpect(jsonPath("$.statusCounts.CANCELLED").value(0))
				.andExpect(jsonPath("$.inventory.lowStockThreshold").value(5));

		mockMvc.perform(get("/api/v1/admin/analytics/orders/timeseries")
						.param("from", empty.from().toString())
						.param("to", empty.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.empty").value(true))
				.andExpect(jsonPath("$.points.length()").value(9))
				.andExpect(jsonPath("$.points[0].date").value(firstDay.toString()))
				.andExpect(jsonPath("$.points[0].placedOrderCount").value(0))
				.andExpect(jsonPath("$.points[0].revenueTotal").value(0));

		mockMvc.perform(get("/api/v1/admin/analytics/products/top")
						.param("from", empty.from().toString())
						.param("to", empty.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.empty").value(true))
				.andExpect(jsonPath("$.items").isEmpty());

		mockMvc.perform(get("/api/v1/admin/analytics/categories/sales")
						.param("from", empty.from().toString())
						.param("to", empty.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.empty").value(true))
				.andExpect(jsonPath("$.items").isEmpty());

		mockMvc.perform(get("/api/v1/admin/analytics/promotions/summary")
						.param("from", empty.from().toString())
						.param("to", empty.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.empty").value(true))
				.andExpect(jsonPath("$.discountTotal").value(0))
				.andExpect(jsonPath("$.ordersWithPromotionCount").value(0))
				.andExpect(jsonPath("$.breakdown").isEmpty());
	}

	@Test
	void formulasAttributionAndZeroFill() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Window window = uniqueWindow(4);
		LocalDate day0 = window.from().atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate day1 = day0.plusDays(1);
		LocalDate day2 = day0.plusDays(2);
		LocalDate day3 = day0.plusDays(3);
		Instant midExclusiveTo = day2.atStartOfDay(ZoneOffset.UTC).toInstant();
		SeededData seeded = seedAnalyticsFixture(suffix, window);

		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.placedOrderCount").value(4))
				.andExpect(jsonPath("$.revenueOrderCount").value(2))
				.andExpect(jsonPath("$.revenueTotal").value(270.00))
				.andExpect(jsonPath("$.averageOrderValue").value(135.00))
				.andExpect(jsonPath("$.cancellationCount").value(1))
				.andExpect(jsonPath("$.cancellationRate").value(0.25))
				.andExpect(jsonPath("$.statusCounts.PAID").value(1))
				.andExpect(jsonPath("$.statusCounts.COMPLETED").value(1))
				.andExpect(jsonPath("$.statusCounts.PENDING_PAYMENT").value(1))
				.andExpect(jsonPath("$.statusCounts.CANCELLED").value(1))
				.andExpect(jsonPath("$.statusCounts.FAILED").value(0))
				.andExpect(jsonPath("$.statusCounts.PROCESSING").value(0))
				.andExpect(jsonPath("$.statusCounts.SHIPPED").value(0))
				.andExpect(jsonPath("$.empty").value(false))
				.andExpect(jsonPath("$.inventory.lowStockThreshold").value(5))
				.andExpect(jsonPath("$.averageOrderValue").exists());

		mockMvc.perform(get("/api/v1/admin/analytics/orders/status")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(4))
				.andExpect(jsonPath("$.counts.PAID").value(1))
				.andExpect(jsonPath("$.counts.FAILED").value(0))
				.andExpect(jsonPath("$.empty").value(false));

		MvcResult series = mockMvc.perform(get("/api/v1/admin/analytics/orders/timeseries")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bucket").value("DAY"))
				.andExpect(jsonPath("$.points.length()").value(4))
				.andExpect(jsonPath("$.points[0].date").value(day0.toString()))
				.andExpect(jsonPath("$.points[0].placedOrderCount").value(2))
				.andExpect(jsonPath("$.points[0].revenueTotal").value(100.00))
				.andExpect(jsonPath("$.points[1].date").value(day1.toString()))
				.andExpect(jsonPath("$.points[1].placedOrderCount").value(0))
				.andExpect(jsonPath("$.points[1].revenueTotal").value(0))
				.andExpect(jsonPath("$.points[2].date").value(day2.toString()))
				.andExpect(jsonPath("$.points[2].placedOrderCount").value(2))
				.andExpect(jsonPath("$.points[2].revenueTotal").value(170.00))
				.andExpect(jsonPath("$.points[3].date").value(day3.toString()))
				.andExpect(jsonPath("$.empty").value(false))
				.andReturn();
		JsonNode points = objectMapper.readTree(series.getResponse().getContentAsString()).get("points");
		assertThat(points.get(3).get("placedOrderCount").asLong()).isZero();

		MvcResult top = mockMvc.perform(get("/api/v1/admin/analytics/products/top")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.param("limit", "10")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].productSku").value("AN-A-" + suffix))
				.andExpect(jsonPath("$.items[0].unitsSold").value(3))
				.andExpect(jsonPath("$.items[0].revenue").value(150.00))
				.andExpect(jsonPath("$.empty").value(false))
				.andReturn();
		JsonNode topItems = objectMapper.readTree(top.getResponse().getContentAsString()).get("items");
		// Null product_id still contributes via sku snapshot but groups separately from live productId
		assertThat(topItems).hasSize(3);
		long bUnits = 0;
		for (JsonNode item : topItems) {
			if (("AN-B-" + suffix).equals(item.get("productSku").asText())) {
				bUnits += item.get("unitsSold").asLong();
			}
		}
		assertThat(bUnits).isEqualTo(2);

		mockMvc.perform(get("/api/v1/admin/analytics/products/top")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.param("limit", "1")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].productSku").value("AN-A-" + suffix));

		mockMvc.perform(get("/api/v1/admin/analytics/categories/sales")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.param("limit", "10")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].categoryId").value(seeded.categoryBId().toString()))
				// Live attribution after A→B move; orphaned B line on paid order excluded (units 4 / 220, not 5 / 270)
				.andExpect(jsonPath("$.items[0].unitsSold").value(4))
				.andExpect(jsonPath("$.items[0].revenue").value(220.00))
				.andExpect(jsonPath("$.attributionNote").isString());

		mockMvc.perform(get("/api/v1/admin/analytics/promotions/summary")
						.param("from", window.from().toString())
						.param("to", window.to().toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discountTotal").value(15.00))
				.andExpect(jsonPath("$.ordersWithPromotionCount").value(1))
				.andExpect(jsonPath("$.revenueOrderCount").value(2))
				.andExpect(jsonPath("$.breakdown.length()").value(1))
				.andExpect(jsonPath("$.breakdown[0].campaignSlug").value("camp-gone-" + suffix))
				.andExpect(jsonPath("$.breakdown[0].campaignName").value("Deleted Camp " + suffix))
				.andExpect(jsonPath("$.breakdown[0].strategyCode").value(""))
				.andExpect(jsonPath("$.breakdown[0].discountTotal").value(15.00))
				.andExpect(jsonPath("$.breakdown[0].orderCount").value(1))
				.andExpect(jsonPath("$.empty").value(false));

		// Exclusive `to`: order at TO boundary must not count; day2 orders also excluded.
		mockMvc.perform(get("/api/v1/admin/analytics/overview")
						.param("from", window.from().toString())
						.param("to", midExclusiveTo.toString())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.placedOrderCount").value(2))
				.andExpect(jsonPath("$.revenueTotal").value(100.00));
	}

	private SeededData seedAnalyticsFixture(String suffix, Window window) {
		return transactionTemplate.execute(status -> {
			LocalDate day0 = window.from().atZone(ZoneOffset.UTC).toLocalDate();
			Instant day0Morning = day0.atTime(10, 0).toInstant(ZoneOffset.UTC);
			Instant day0Evening = day0.atTime(18, 0).toInstant(ZoneOffset.UTC);
			Instant day2Morning = day0.plusDays(2).atTime(8, 0).toInstant(ZoneOffset.UTC);
			Instant day2Evening = day0.plusDays(2).atTime(20, 0).toInstant(ZoneOffset.UTC);

			UserAccount user = userAccountRepository.saveAndFlush(UserAccount.createNew(
					"analytics." + suffix + "@example.com",
					"{noop}Password1!",
					"Analytics User"
			));
			Address address = addressRepository.saveAndFlush(Address.createNew(
					user, "Recipient", "Line 1", null, "Hanoi", null, "100000", "VN", "0900000000", true
			));

			Category catA = categoryRepository.saveAndFlush(Category.createNew(
					"Cat A " + suffix, "cat-a-" + suffix, null, null
			));
			Category catB = categoryRepository.saveAndFlush(Category.createNew(
					"Cat B " + suffix, "cat-b-" + suffix, null, null
			));
			Product productA = productRepository.saveAndFlush(Product.createNew(
					catA, "AN-A-" + suffix, "Product A " + suffix, null, new BigDecimal("50.00"), "VND", true
			));
			Product productB = productRepository.saveAndFlush(Product.createNew(
					catB, "AN-B-" + suffix, "Product B " + suffix, null, new BigDecimal("60.00"), "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(productA, 20));
			inventoryRepository.saveAndFlush(Inventory.forProduct(productB, 20));

			// Day 1: PAID revenue 100 (1x A) + PENDING ignored for revenue
			ShopOrder paid = baseOrder(user, address, OrderStatus.PAID, day0Morning,
					new BigDecimal("100.00"), BigDecimal.ZERO);
			paid.addItem(OrderItem.snapshot(productA, 1, new BigDecimal("50.00")));
			paid.addItem(OrderItem.snapshot(productB, 1, new BigDecimal("50.00")));
			recomputeLineTotals(paid);
			shopOrderRepository.saveAndFlush(paid);

			ShopOrder pending = baseOrder(user, address, OrderStatus.PENDING_PAYMENT,
					day0Evening, new BigDecimal("40.00"), BigDecimal.ZERO);
			pending.addItem(OrderItem.snapshot(productA, 1, new BigDecimal("40.00")));
			shopOrderRepository.saveAndFlush(pending);

			PromotionStrategy strategy = strategyRepository.saveAndFlush(PromotionStrategy.createNew(
					"Strat " + suffix,
					"S-" + suffix,
					PromotionStrategyType.PERCENTAGE_DISCOUNT,
					new BigDecimal("10.00"),
					null,
					null,
					null,
					null,
					false,
					100,
					true
			));
			PromotionCampaign campaign = PromotionCampaign.createNew(
					strategy,
					"Deleted Camp " + suffix,
					"camp-gone-" + suffix,
					window.from().minus(30, ChronoUnit.DAYS),
					window.to().plus(30, ChronoUnit.DAYS),
					null
			);
			campaign.setStatus(PromotionCampaignStatus.ACTIVE);
			campaign = campaignRepository.saveAndFlush(campaign);

			// Day 3: COMPLETED with promo (revenue 170 after discount bookkeeping on total)
			ShopOrder completed = baseOrder(user, address, OrderStatus.COMPLETED,
					day2Morning, new BigDecimal("170.00"), new BigDecimal("15.00"));
			completed.addItem(OrderItem.snapshot(productA, 2, new BigDecimal("50.00")));
			completed.addItem(OrderItem.snapshot(productB, 1, new BigDecimal("70.00")));
			completed.addPromotionSnapshot(OrderPromotionSnapshot.capture(
					campaign.getId(),
					strategy.getId(),
					"Deleted Camp " + suffix,
					"camp-gone-" + suffix,
					"Percent Off",
					PromotionStrategyType.PERCENTAGE_DISCOUNT.name(),
					null,
					100,
					false,
					new BigDecimal("10.00"),
					null,
					null,
					new BigDecimal("185.00"),
					new BigDecimal("15.00"),
					"VND"
			));
			shopOrderRepository.saveAndFlush(completed);
			campaignRepository.delete(campaign);
			campaignRepository.flush();

			ShopOrder cancelled = baseOrder(user, address, OrderStatus.CANCELLED,
					day2Evening, new BigDecimal("99.00"), BigDecimal.ZERO);
			cancelled.addItem(OrderItem.snapshot(productB, 1, new BigDecimal("99.00")));
			shopOrderRepository.saveAndFlush(cancelled);

			// Boundary exclusive: should not appear for [from, to)
			ShopOrder atTo = baseOrder(user, address, OrderStatus.PAID, window.to(), new BigDecimal("999.00"), BigDecimal.ZERO);
			atTo.addItem(OrderItem.snapshot(productA, 1, new BigDecimal("999.00")));
			shopOrderRepository.saveAndFlush(atTo);

			// Recategorize product A → cat B (live attribution); orphan one historical line product_id
			productA.setCategory(catB);
			productRepository.saveAndFlush(productA);

			ShopOrder orphanHolder = shopOrderRepository.findByIdWithItems(paid.getId()).orElseThrow();
			OrderItem orphanLine = orphanHolder.getItems().stream()
					.filter(i -> i.getProductSku().equals("AN-B-" + suffix))
					.findFirst()
					.orElseThrow();
			// Keep sku snapshot; clear live product so category sales exclude this line
			orphanLine.setProduct(null);
			shopOrderRepository.saveAndFlush(orphanHolder);

			return new SeededData(catA.getId(), catB.getId(), productA.getId(), productB.getId());
		});
	}

	/** Unique future UTC day-aligned window to avoid pollution from prior IT runs. */
	private static Window uniqueWindow(int days) {
		long offsetDays = 40_000L + ThreadLocalRandom.current().nextLong(0, 200_000);
		Instant from = Instant.parse("2000-01-01T00:00:00Z").plus(offsetDays, ChronoUnit.DAYS);
		return new Window(from, from.plus(days, ChronoUnit.DAYS));
	}

	private record Window(Instant from, Instant to) {
	}

	private static ShopOrder baseOrder(
			UserAccount user,
			Address address,
			OrderStatus status,
			Instant placedAt,
			BigDecimal total,
			BigDecimal discount
	) {
		ShopOrder order = ShopOrder.createPending(user, address);
		order.setStatus(status);
		order.setCurrency("VND");
		order.setSubtotalAmount(total.add(discount));
		order.setShippingAmount(BigDecimal.ZERO);
		order.setDiscountAmount(discount);
		order.setTotalAmount(total);
		order.setPlacedAt(placedAt);
		return order;
	}

	private static void recomputeLineTotals(ShopOrder order) {
		BigDecimal sub = order.getItems().stream()
				.map(OrderItem::getLineTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		order.setSubtotalAmount(sub);
	}

	private record SeededData(UUID categoryAId, UUID categoryBId, UUID productAId, UUID productBId) {
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.analytics@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.analytics@example.com", List.of("CUSTOMER"));
	}
}
