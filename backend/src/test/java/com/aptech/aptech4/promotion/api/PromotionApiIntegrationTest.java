package com.aptech.aptech4.promotion.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PromotionApiIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		IntegrationTestSupport.registerComposeDataSources(registry, "aptech4_catalog_it");
		IntegrationTestSupport.registerSecurityProperties(registry);
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
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void adminOnlyMutationsAndAnonymousPublicReads() throws Exception {
		String admin = adminToken();
		String customer = customerToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(get("/api/v1/admin/promotions/strategies"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero").isArray());
		mockMvc.perform(get("/api/v1/promotions/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isOk());

		MvcResult strategyCreated = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Banner Strat %s",
								  "type": "BANNER_ONLY",
								  "stackable": false,
								  "priority": 10,
								  "active": true
								}
								""".formatted(suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("BANNER_ONLY"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn();
		String strategyId = objectMapper.readTree(strategyCreated.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "Nope", "type": "BANNER_ONLY" }
								"""))
				.andExpect(status().isForbidden());

		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(2, ChronoUnit.HOURS);
		MvcResult campaignCreated = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Home Banner %s",
								  "slug": "home-banner-%s",
								  "startsAt": "%s",
								  "endsAt": "%s",
								  "notes": "p19"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.placementEffective").value(false))
				.andReturn();
		String campaignId = objectMapper.readTree(campaignCreated.getResponse().getContentAsString()).get("id").asText();

		String imageUrl = promoImageUrl();
		MvcResult placementCreated = mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "placementKey": "HOME_HERO",
								  "title": "Hero %s",
								  "subtitle": "Save now",
								  "ctaLabel": "Shop",
								  "ctaType": "NONE",
								  "desktopImageUrl": "%s",
								  "displayOrder": 1,
								  "enabled": true
								}
								""".formatted(suffix, imageUrl)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.placementKey").value("HOME_HERO"))
				.andReturn();
		String placementId = objectMapper.readTree(placementCreated.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero").isEmpty());

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.placementEffective").value(true))
				.andExpect(jsonPath("$.effectiveStatus").value("ACTIVE"));

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero.length()").value(1))
				.andExpect(jsonPath("$.hero[0].title").value("Hero " + suffix))
				.andExpect(jsonPath("$.hero[0].desktopImageUrl").value(imageUrl));

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/pause")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAUSED"))
				.andExpect(jsonPath("$.placementEffective").value(false));

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero").isEmpty());

		mockMvc.perform(delete("/api/v1/admin/promotions/placements/" + placementId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/admin/promotions/campaigns/" + campaignId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isNoContent());
	}

	@Test
	void strategyValidationAndCampaignScheduleRules() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "Bad Banner %s", "type": "BANNER_ONLY", "discountPercent": 10 }
								""".formatted(suffix)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "Pct %s", "type": "PERCENTAGE_DISCOUNT" }
								""".formatted(suffix)))
				.andExpect(status().isBadRequest());

		MvcResult strategy = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Pct OK %s",
								  "type": "PERCENTAGE_DISCOUNT",
								  "discountPercent": 15,
								  "active": true
								}
								""".formatted(suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		String strategyId = objectMapper.readTree(strategy.getResponse().getContentAsString()).get("id").asText();

		Instant pastStart = Instant.now().minus(3, ChronoUnit.HOURS);
		Instant pastEnd = Instant.now().minus(1, ChronoUnit.HOURS);
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Bad Window %s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, pastEnd, pastStart)))
				.andExpect(status().isBadRequest());

		Instant starts = Instant.now().minus(2, ChronoUnit.HOURS);
		Instant ends = Instant.now().minus(30, ChronoUnit.MINUTES);
		MvcResult expiredWindow = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Ended %s",
								  "slug": "ended-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isCreated())
				.andReturn();
		String endedId = objectMapper.readTree(expiredWindow.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + endedId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isBadRequest());

		Instant futureStart = Instant.now().plus(1, ChronoUnit.DAYS);
		Instant futureEnd = Instant.now().plus(2, ChronoUnit.DAYS);
		MvcResult futureCampaign = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Future %s",
								  "slug": "future-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, futureStart, futureEnd)))
				.andExpect(status().isCreated())
				.andReturn();
		String futureId = objectMapper.readTree(futureCampaign.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + futureId + "/schedule")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SCHEDULED"));

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + futureId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.effectiveStatus").value("SCHEDULED"))
				.andExpect(jsonPath("$.placementEffective").value(false))
				.andExpect(jsonPath("$.pricingEffective").value(false));

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + futureId + "/preview")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "at": "%s" }
								""".formatted(futureStart.plus(1, ChronoUnit.HOURS))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.effectiveStatus").value("ACTIVE"))
				.andExpect(jsonPath("$.placementEffective").value(true))
				.andExpect(jsonPath("$.pricingEffective").value(true));
	}

	@Test
	void productAndCategoryEligibilityValidation() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		UUID[] ids = new UUID[2];
		transactionTemplate.executeWithoutResult(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Promo Cat " + suffix, "promo-cat-" + suffix, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category,
					"PR-" + suffix,
					"Promo Product " + suffix,
					null,
					new BigDecimal("10000"),
					"VND",
					true
			));
			ids[0] = category.getId();
			ids[1] = product.getId();
		});
		UUID categoryId = ids[0];
		UUID productId = ids[1];

		MvcResult productStrategy = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Product Disc %s",
								  "type": "PRODUCT_DISCOUNT",
								  "discountPercent": 10,
								  "active": true
								}
								""".formatted(suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		String productStrategyId = objectMapper.readTree(productStrategy.getResponse().getContentAsString())
				.get("id").asText();

		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Need Products %s",
								  "slug": "need-products-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(productStrategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isBadRequest());

		MvcResult campaign = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "With Products %s",
								  "slug": "with-products-%s",
								  "startsAt": "%s",
								  "endsAt": "%s",
								  "productIds": ["%s"]
								}
								""".formatted(productStrategyId, suffix, suffix, starts, ends, productId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productIds[0]").value(productId.toString()))
				.andReturn();
		String campaignId = objectMapper.readTree(campaign.getResponse().getContentAsString()).get("id").asText();

		MvcResult categoryStrategy = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Cat Disc %s",
								  "type": "CATEGORY_DISCOUNT",
								  "discountAmount": 5000,
								  "active": true
								}
								""".formatted(suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		String categoryStrategyId = objectMapper.readTree(categoryStrategy.getResponse().getContentAsString())
				.get("id").asText();

		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/" + campaignId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Switched to cat %s",
								  "slug": "with-products-%s",
								  "startsAt": "%s",
								  "endsAt": "%s",
								  "categoryIds": ["%s"]
								}
								""".formatted(categoryStrategyId, suffix, suffix, starts, ends, categoryId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.strategy.type").value("CATEGORY_DISCOUNT"))
				.andExpect(jsonPath("$.categoryIds[0]").value(categoryId.toString()));

		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/" + campaignId + "/eligibility")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productIds": [], "categoryIds": [] }
								"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/" + campaignId + "/eligibility")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productIds": [], "categoryIds": ["%s"] }
								""".formatted(categoryId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoryIds.length()").value(1));
	}

	@Test
	void publicPlacementOrderingStatusAndUtcWindowFiltering() throws Exception {
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		String strategyId = createBannerStrategy(admin, "Order Strat " + suffix);

		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
		String campaignId = createCampaign(admin, strategyId, "Order Camp " + suffix, "order-camp-" + suffix, starts, ends);
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());

		String imgA = promoImageUrl();
		String imgB = promoImageUrl();
		String imgC = promoImageUrl();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson("HOME_HERO", "Second", imgB, 20, true, null, null)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson("HOME_HERO", "First", imgA, 10, true, null, null)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson(
								"HOME_HERO",
								"Future",
								imgC,
								5,
								true,
								Instant.now().plus(1, ChronoUnit.DAYS),
								Instant.now().plus(2, ChronoUnit.DAYS)
						)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson("HOME_PROMO_TOP", "Promo Top", promoImageUrl(), 1, true, null, null)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson("HOME_PROMO_BOTTOM", "Promo Bottom", promoImageUrl(), 1, false, null, null)))
				.andExpect(status().isCreated());

		MvcResult homepage = mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero.length()").value(2))
				.andExpect(jsonPath("$.hero[0].title").value("First"))
				.andExpect(jsonPath("$.hero[1].title").value("Second"))
				.andExpect(jsonPath("$.promoTop.title").value("Promo Top"))
				.andExpect(jsonPath("$.promoBottom").value(nullValue()))
				.andReturn();
		JsonNode home = objectMapper.readTree(homepage.getResponse().getContentAsString());
		assertThat(home.get("promoBottom").isNull()).isTrue();

		mockMvc.perform(get("/api/v1/promotions/placements").param("keys", "HOME_HERO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].title").value("First"));

		mockMvc.perform(patch("/api/v1/admin/promotions/strategies/" + strategyId + "/status")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "active": false }
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero").isEmpty())
				.andExpect(jsonPath("$.promoTop").value(nullValue()));
	}

	@Test
	void emptyPublicPlacementsWhenNoEffectiveCreatives() throws Exception {
		mockMvc.perform(get("/api/v1/promotions/homepage")
						.param("_bust", UUID.randomUUID().toString()))
				.andExpect(status().isOk());

		// Dedicated inactive campaign must not leak into public response
		String admin = adminToken();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String strategyId = createBannerStrategy(admin, "Empty Strat " + suffix);
		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
		String campaignId = createCampaign(admin, strategyId, "Empty Camp " + suffix, "empty-camp-" + suffix, starts, ends);
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content(placementJson("HOME_HERO", "Draft only", promoImageUrl(), 1, true, null, null)))
				.andExpect(status().isCreated());

		MvcResult beforeActivate = mockMvc.perform(get("/api/v1/promotions/placements").param("keys", "HOME_HERO"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode before = objectMapper.readTree(beforeActivate.getResponse().getContentAsString());
		assertThat(before.isArray()).isTrue();
		for (JsonNode node : before) {
			assertThat(node.get("title").asText()).isNotEqualTo("Draft only");
		}
	}

	private String createBannerStrategy(String admin, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "%s", "type": "BANNER_ONLY", "active": true, "priority": 50 }
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createCampaign(
			String admin,
			String strategyId,
			String name,
			String slug,
			Instant starts,
			Instant ends
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "%s",
								  "slug": "%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, name, slug, starts, ends)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private static String placementJson(
			String key,
			String title,
			String imageUrl,
			int order,
			boolean enabled,
			Instant startsAt,
			Instant endsAt
	) {
		String starts = startsAt == null ? "null" : "\"" + startsAt + "\"";
		String ends = endsAt == null ? "null" : "\"" + endsAt + "\"";
		return """
				{
				  "placementKey": "%s",
				  "title": "%s",
				  "ctaType": "NONE",
				  "desktopImageUrl": "%s",
				  "displayOrder": %d,
				  "enabled": %s,
				  "startsAt": %s,
				  "endsAt": %s
				}
				""".formatted(key, title, imageUrl, order, enabled, starts, ends);
	}

	private static String promoImageUrl() {
		return "/uploads/promotions/" + UUID.randomUUID() + ".jpg";
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.promo@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.promo@example.com", List.of("CUSTOMER"));
	}
}
