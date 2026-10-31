package com.aptech.aptech4.promotion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PromotionCampaignEffectivenessTest {

	@Test
	void activeFutureStartIsNotYetEffective() {
		PromotionStrategy strategy = percentStrategy();
		Instant start = Instant.parse("2026-11-10T00:00:00Z");
		Instant end = Instant.parse("2026-11-20T00:00:00Z");
		PromotionCampaign campaign = PromotionCampaign.createNew(strategy, "Sale", "sale", start, end, null);
		campaign.setStatus(PromotionCampaignStatus.ACTIVE);

		assertThat(campaign.isPricingEffective(Instant.parse("2026-11-09T23:59:59Z"))).isFalse();
		assertThat(campaign.isPricingEffective(Instant.parse("2026-11-10T00:00:00Z"))).isTrue();
		assertThat(campaign.isPricingEffective(Instant.parse("2026-11-20T00:00:00Z"))).isTrue();
		assertThat(campaign.isPricingEffective(Instant.parse("2026-11-20T00:00:01Z"))).isFalse();
	}

	@Test
	void copiesPriorityAndStackableFromStrategy() {
		PromotionStrategy strategy = percentStrategy();
		strategy.applyUpdate(
				strategy.getName(),
				strategy.getCode(),
				strategy.getType(),
				strategy.getDiscountPercent(),
				null,
				null,
				null,
				null,
				true,
				250,
				true
		);
		PromotionCampaign campaign = PromotionCampaign.createNew(
				strategy,
				"Copied",
				"copied",
				Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-30T00:00:00Z"),
				null
		);
		assertThat(campaign.getPriority()).isEqualTo(250);
		assertThat(campaign.isStackable()).isTrue();

		strategy.applyUpdate(
				strategy.getName(),
				strategy.getCode(),
				strategy.getType(),
				strategy.getDiscountPercent(),
				null,
				null,
				null,
				null,
				false,
				1,
				true
		);
		assertThat(campaign.getPriority()).isEqualTo(250);
		assertThat(campaign.isStackable()).isTrue();
	}

	@Test
	void bannerOnlyNeverPricingEffective() {
		PromotionStrategy banner = PromotionStrategy.createNew(
				"Banner", null, PromotionStrategyType.BANNER_ONLY,
				null, null, null, null, null, false, 100, true
		);
		PromotionCampaign campaign = PromotionCampaign.createNew(
				banner,
				"Hero",
				"hero",
				Instant.parse("2026-11-01T00:00:00Z"),
				Instant.parse("2026-11-30T00:00:00Z"),
				null
		);
		campaign.setStatus(PromotionCampaignStatus.ACTIVE);
		Instant now = Instant.parse("2026-11-15T00:00:00Z");
		assertThat(campaign.isPricingEffective(now)).isFalse();
		assertThat(campaign.isPlacementEffective(now)).isTrue();
	}

	private static PromotionStrategy percentStrategy() {
		return PromotionStrategy.createNew(
				"Pct",
				null,
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10"),
				null,
				null,
				null,
				null,
				false,
				100,
				true
		);
	}
}
