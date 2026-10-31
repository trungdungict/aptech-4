package com.aptech.aptech4.promotion.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

class PromotionCacheKeysTest {

	@Test
	void placementsKeyIsStableAndSorted() {
		assertThat(PromotionCacheKeys.placements()).isEqualTo("ALL");
		assertThat(PromotionCacheKeys.placements(List.of())).isEqualTo("ALL");
		assertThat(PromotionCacheKeys.placements(
				PromotionPlacementKey.HOME_PROMO_BOTTOM,
				PromotionPlacementKey.HOME_HERO
		)).isEqualTo("HOME_HERO,HOME_PROMO_BOTTOM");
	}
}
