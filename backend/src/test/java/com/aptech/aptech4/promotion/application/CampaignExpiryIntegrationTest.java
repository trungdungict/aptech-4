package com.aptech.aptech4.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.order.domain.OrderPromotionSnapshot;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.OrderPromotionSnapshotRepository;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.aptech.aptech4.promotion.api.HomepagePromotionsResponse;
import com.aptech.aptech4.promotion.cache.PromotionCacheKeys;
import com.aptech.aptech4.promotion.cache.PromotionCacheNames;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;

@SpringBootTest
@Import(CampaignExpiryIntegrationTest.FixedClockConfig.class)
class CampaignExpiryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock testUtcClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

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
		// Avoid scheduler noise during this suite
		registry.add("aptech.promotions.expiry-poll-ms", () -> "3600000");
		registry.add("aptech.inventory.expiry-poll-ms", () -> "3600000");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	PromotionCampaignService campaignService;

	@Autowired
	CampaignExpiryJob campaignExpiryJob;

	@Autowired
	Clock clock;

	@Autowired
	PromotionCampaignRepository campaignRepository;

	@Autowired
	PromotionStrategyRepository strategyRepository;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	ShopOrderRepository shopOrderRepository;

	@Autowired
	OrderPromotionSnapshotRepository snapshotRepository;

	@Autowired
	CacheManager cacheManager;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Test
	void expireDue_activePastEnd_becomesExpired_andIsIdempotent() {
		assertThat(clock.instant()).isEqualTo(NOW);
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		UUID pastId = seedCampaign(
				suffix + "-past",
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-15T11:59:59Z")
		);
		UUID atEndId = seedCampaign(
				suffix + "-at-end",
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2026-11-01T00:00:00Z"),
				NOW
		);
		UUID futureId = seedCampaign(
				suffix + "-future",
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-12-01T00:00:00Z")
		);
		UUID pausedPastId = seedCampaign(
				suffix + "-paused",
				PromotionCampaignStatus.PAUSED,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z")
		);
		UUID scheduledPastId = seedCampaign(
				suffix + "-sched",
				PromotionCampaignStatus.SCHEDULED,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z")
		);
		UUID draftPastId = seedCampaign(
				suffix + "-draft",
				PromotionCampaignStatus.DRAFT,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-01T00:00:00Z")
		);

		int first = campaignService.expireDueCampaigns();
		assertThat(first).isGreaterThanOrEqualTo(1);

		assertStatus(pastId, PromotionCampaignStatus.EXPIRED);
		assertStatus(atEndId, PromotionCampaignStatus.ACTIVE);
		assertStatus(futureId, PromotionCampaignStatus.ACTIVE);
		assertStatus(pausedPastId, PromotionCampaignStatus.PAUSED);
		assertStatus(scheduledPastId, PromotionCampaignStatus.SCHEDULED);
		assertStatus(draftPastId, PromotionCampaignStatus.DRAFT);

		int second = campaignService.expireDueCampaigns();
		assertThat(second).isZero();
		assertStatus(pastId, PromotionCampaignStatus.EXPIRED);
	}

	@Test
	void expireDue_invalidatesPublicPromotionCaches() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		seedCampaign(
				suffix + "-cache",
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-15T11:00:00Z")
		);

		Cache homepageCache = cacheManager.getCache(PromotionCacheNames.HOMEPAGE);
		Cache placementsCache = cacheManager.getCache(PromotionCacheNames.PLACEMENTS);
		assertThat(homepageCache).isNotNull();
		assertThat(placementsCache).isNotNull();

		// Seed entries directly so the assertion targets expiry→invalidate (R7), not @Cacheable PUT reliability.
		String placementsKey = PromotionCacheKeys.placements(List.of());
		homepageCache.put(PromotionCacheKeys.homepage(), HomepagePromotionsResponse.empty());
		placementsCache.put(placementsKey, List.of());
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNotNull();
		assertThat(placementsCache.get(placementsKey)).isNotNull();

		int expired = campaignService.expireDueCampaigns();
		assertThat(expired).isGreaterThanOrEqualTo(1);
		assertThat(homepageCache.get(PromotionCacheKeys.homepage())).isNull();
		assertThat(placementsCache.get(placementsKey)).isNull();
	}

	@Test
	void jobDelegates_andDoesNotTouchOrderSnapshots() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UUID campaignId = seedCampaign(
				suffix + "-snap",
				PromotionCampaignStatus.ACTIVE,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-11-15T11:00:00Z")
		);

		UUID snapshotId = transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.saveAndFlush(UserAccount.createNew(
					"expiry." + suffix + "@example.com",
					"{noop}Password1!",
					"Expiry User"
			));
			Address address = addressRepository.saveAndFlush(Address.createNew(
					user, "Recipient", "Line 1", null, "Hanoi", null, "100000", "VN", "0900000000", true
			));
			ShopOrder order = ShopOrder.createPending(user, address);
			order.setCurrency("VND");
			order.setSubtotalAmount(new BigDecimal("100.00"));
			order.setShippingAmount(BigDecimal.ZERO);
			order.setDiscountAmount(new BigDecimal("10.00"));
			order.setTotalAmount(new BigDecimal("90.00"));
			order.setPromotionSummary("Camp " + suffix + "-snap");
			order.addPromotionSnapshot(OrderPromotionSnapshot.capture(
					campaignId,
					null,
					"Camp " + suffix + "-snap",
					"camp-" + suffix + "-snap",
					"Strat",
					PromotionStrategyType.PERCENTAGE_DISCOUNT.name(),
					null,
					100,
					false,
					new BigDecimal("10.00"),
					null,
					null,
					new BigDecimal("100.00"),
					new BigDecimal("10.00"),
					"VND"
			));
			order = shopOrderRepository.saveAndFlush(order);
			return snapshotRepository.findByOrderIdOrderByAppliedAtAscIdAsc(order.getId()).getFirst().getId();
		});

		campaignExpiryJob.expireDue();

		assertStatus(campaignId, PromotionCampaignStatus.EXPIRED);

		transactionTemplate.executeWithoutResult(status -> {
			OrderPromotionSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
			assertThat(snapshot.getCampaignId()).isEqualTo(campaignId);
			assertThat(snapshot.getDiscountAmount()).isEqualByComparingTo("10.00");
			assertThat(snapshot.getCampaignName()).isEqualTo("Camp " + suffix + "-snap");
			ShopOrder order = snapshot.getOrder();
			assertThat(order.getDiscountAmount()).isEqualByComparingTo("10.00");
			assertThat(order.getTotalAmount()).isEqualByComparingTo("90.00");
		});
	}

	private void assertStatus(UUID campaignId, PromotionCampaignStatus expected) {
		transactionTemplate.executeWithoutResult(status -> {
			PromotionCampaign campaign = campaignRepository.findById(campaignId).orElseThrow();
			assertThat(campaign.getStatus()).isEqualTo(expected);
		});
	}

	private UUID seedCampaign(
			String suffix,
			PromotionCampaignStatus status,
			Instant startsAt,
			Instant endsAt
	) {
		return transactionTemplate.execute(tx -> {
			PromotionStrategy strategy = strategyRepository.saveAndFlush(PromotionStrategy.createNew(
					"Strat " + suffix,
					"X-" + suffix,
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
					"Camp " + suffix,
					"camp-" + suffix,
					startsAt,
					endsAt,
					null
			);
			campaign.setStatus(status);
			return campaignRepository.saveAndFlush(campaign).getId();
		});
	}
}
