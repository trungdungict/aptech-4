package com.aptech.aptech4.promotion.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.aptech.aptech4.identity.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PromotionLocalizationIntegrationTest {

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

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void publicPlacementAndAdminTranslationsSupportViEnFallbackAndRbac() throws Exception {
		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.promo.loc@example.com", List.of("ADMIN"));
		String customer = jwtService.createAccessToken(UUID.randomUUID(), "cust.promo.loc@example.com", List.of("CUSTOMER"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);

		MvcResult strategy = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Chiến lược VI %s",
								  "code": "LOC-%s",
								  "type": "BANNER_ONLY",
								  "active": true
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andReturn();
		String strategyId = objectMapper.readTree(strategy.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(put("/api/v1/admin/promotions/strategies/{id}/translations", strategyId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "translations": { "vi": "Chiến lược VI %s", "en": "Strategy EN %s" } }
								""".formatted(suffix, suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.translations.en").value("Strategy EN " + suffix));

		mockMvc.perform(get("/api/v1/admin/promotions/strategies/{id}/translations", strategyId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
				.andExpect(status().isForbidden());

		MvcResult campaign = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Chiến dịch VI %s",
								  "slug": "camp-loc-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isCreated())
				.andReturn();
		String campaignId = objectMapper.readTree(campaign.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/{id}/translations", campaignId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "translations": { "vi": "Chiến dịch VI %s", "en": "Campaign EN %s" } }
								""".formatted(suffix, suffix)))
				.andExpect(status().isOk());

		String imageUrl = "/uploads/promotions/" + UUID.randomUUID() + ".jpg";
		MvcResult placement = mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "placementKey": "HOME_HERO",
								  "title": "Banner VI %s",
								  "subtitle": "Phụ đề VI",
								  "ctaLabel": "Mua ngay",
								  "ctaType": "NONE",
								  "desktopImageUrl": "%s",
								  "displayOrder": 1,
								  "enabled": true
								}
								""".formatted(suffix, imageUrl)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode placementBody = objectMapper.readTree(placement.getResponse().getContentAsString());
		String placementId = placementBody.get("id").asText();

		mockMvc.perform(put("/api/v1/admin/promotions/placements/{id}/translations", placementId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "translations": {
								    "vi": { "title": "Banner VI %s", "subtitle": "Phụ đề VI", "ctaLabel": "Mua ngay" },
								    "en": { "title": "Banner EN %s", "subtitle": "Subtitle EN", "ctaLabel": "Buy now" }
								  }
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.translations.en.title").value("Banner EN " + suffix));

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/promotions/homepage").header(HttpHeaders.ACCEPT_LANGUAGE, "vi"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero[0].title").value("Banner VI " + suffix))
				.andExpect(jsonPath("$.hero[0].subtitle").value("Phụ đề VI"))
				.andExpect(jsonPath("$.hero[0].ctaLabel").value("Mua ngay"));

		mockMvc.perform(get("/api/v1/promotions/homepage").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hero[0].title").value("Banner EN " + suffix))
				.andExpect(jsonPath("$.hero[0].subtitle").value("Subtitle EN"))
				.andExpect(jsonPath("$.hero[0].ctaLabel").value("Buy now"));

		// Placement without EN row falls back to VI base (create second placement, no EN translation)
		MvcResult onlyVi = mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "placementKey": "HOME_PROMO_TOP",
								  "title": "Top VI %s",
								  "subtitle": "Top phụ đề",
								  "ctaLabel": "Xem",
								  "ctaType": "NONE",
								  "desktopImageUrl": "/uploads/promotions/%s.jpg",
								  "displayOrder": 2,
								  "enabled": true
								}
								""".formatted(suffix, UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andReturn();
		// Force-clear EN if sync created only VI — public EN should still show VI title
		mockMvc.perform(get("/api/v1/promotions/homepage").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.promoTop.title").value("Top VI " + suffix));

		mockMvc.perform(put("/api/v1/admin/promotions/placements/{id}/translations",
						objectMapper.readTree(onlyVi.getResponse().getContentAsString()).get("id").asText())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "translations": { "de": { "title": "Nein" } } }
								"""))
				.andExpect(status().isBadRequest());
	}
}
