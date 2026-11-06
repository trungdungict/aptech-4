package com.aptech.aptech4.promotion.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.promotion.api.HomepagePromotionsResponse;
import com.aptech.aptech4.promotion.application.PublicPromotionService;
import com.aptech.aptech4.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Public promotion Redis cache hit/miss/invalidation (ADR-020 R7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PromotionCacheIntegrationTest {

	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
	private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
			.withDatabaseName("aptech4_promo_cache_it")
			.withUsername("aptech")
			.withPassword("aptech_tc");

	@Container
	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> String.valueOf(REDIS.getMappedPort(6379)));
		IntegrationTestSupport.registerSecurityProperties(registry);
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PublicPromotionService publicPromotionService;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	PromotionCacheInvalidator promotionCacheInvalidator;

	@Autowired
	JwtService jwtService;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@BeforeEach
	void clearCaches() {
		promotionCacheInvalidator.onPromotionMutation();
	}

	@Test
	void homepageCacheMissAfterAdminMutationWithoutWaitingForTtl() throws Exception {
		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.pcache@example.com", List.of("ADMIN"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		HomepagePromotionsResponse empty = publicPromotionService.homepage();
		assertThat(empty.hero()).isEmpty();
		Cache homepageCache = cacheManager.getCache(PromotionCacheNames.HOMEPAGE);
		assertThat(homepageCache).isNotNull();
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNotNull();

		String strategyId = createBannerStrategy(admin, "Cache Strat " + suffix);
		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
		String campaignId = createAndActivateCampaign(admin, strategyId, suffix, starts, ends);
		String imageUrl = "/uploads/promotions/" + UUID.randomUUID() + ".jpg";

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "placementKey": "HOME_HERO",
								  "title": "Cached Hero %s",
								  "ctaType": "NONE",
								  "desktopImageUrl": "%s",
								  "displayOrder": 1,
								  "enabled": true
								}
								""".formatted(suffix, imageUrl)))
				.andExpect(status().isCreated());

		// Mutation must invalidate — without waiting for TTL the next read is a miss then fresh put.
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNull();

		HomepagePromotionsResponse after = publicPromotionService.homepage();
		assertThat(after.hero()).hasSize(1);
		assertThat(after.hero().getFirst().title()).isEqualTo("Cached Hero " + suffix);
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNotNull();

		mockMvc.perform(put("/api/v1/admin/promotions/campaigns/" + campaignId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Renamed %s",
								  "slug": "cache-camp-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isOk());
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNull();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/pause")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNull();

		HomepagePromotionsResponse paused = publicPromotionService.homepage();
		assertThat(paused.hero()).isEmpty();
	}

	@Test
	void placementsCacheInvalidatedOnActivate() throws Exception {
		String admin = jwtService.createAccessToken(UUID.randomUUID(), "admin.pcache2@example.com", List.of("ADMIN"));
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		publicPromotionService.listPlacements(List.of());
		Cache placementsCache = cacheManager.getCache(PromotionCacheNames.PLACEMENTS);
		assertThat(placementsCache).isNotNull();
		assertThat(placementsCache.get(PromotionCacheKeys.placements())).isNotNull();

		String strategyId = createBannerStrategy(admin, "Act Strat " + suffix);
		Instant starts = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant ends = Instant.now().plus(1, ChronoUnit.DAYS);
		MvcResult campaign = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Act Camp %s",
								  "slug": "act-camp-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isCreated())
				.andReturn();
		String campaignId = objectMapper.readTree(campaign.getResponse().getContentAsString()).get("id").asText();
		assertThat(placementsCache.get(PromotionCacheKeys.placements())).isNull();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/placements")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "placementKey": "HOME_PROMO_TOP",
								  "title": "Top %s",
								  "ctaType": "NONE",
								  "desktopImageUrl": "/uploads/promotions/%s.jpg",
								  "displayOrder": 1,
								  "enabled": true
								}
								""".formatted(suffix, UUID.randomUUID())))
				.andExpect(status().isCreated());

		publicPromotionService.listPlacements(List.of());
		assertThat(placementsCache.get(PromotionCacheKeys.placements())).isNotNull();

		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());
		assertThat(placementsCache.get(PromotionCacheKeys.placements())).isNull();

		mockMvc.perform(get("/api/v1/promotions/homepage"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.promoTop.title").value("Top " + suffix));
	}

	private String createBannerStrategy(String admin, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/admin/promotions/strategies")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "name": "%s", "type": "BANNER_ONLY", "active": true }
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createAndActivateCampaign(
			String admin,
			String strategyId,
			String suffix,
			Instant starts,
			Instant ends
	) throws Exception {
		MvcResult campaign = mockMvc.perform(post("/api/v1/admin/promotions/campaigns")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "strategyId": "%s",
								  "name": "Cache Camp %s",
								  "slug": "cache-camp-%s",
								  "startsAt": "%s",
								  "endsAt": "%s"
								}
								""".formatted(strategyId, suffix, suffix, starts, ends)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode body = objectMapper.readTree(campaign.getResponse().getContentAsString());
		String campaignId = body.get("id").asText();
		mockMvc.perform(post("/api/v1/admin/promotions/campaigns/" + campaignId + "/activate")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isOk());
		return campaignId;
	}
}
