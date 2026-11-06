package com.aptech.aptech4.promotion.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.common.locale.RequestLocaleHolder;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

class PromotionCacheKeysTest {

	@AfterEach
	void clearLocale() {
		RequestLocaleHolder.clear();
	}

	@Test
	void placementsKeyIsStableAndSorted() {
		RequestLocaleHolder.set(AppLocale.VI);
		assertThat(PromotionCacheKeys.placements()).isEqualTo("ALL|vi");
		assertThat(PromotionCacheKeys.placements(List.of())).isEqualTo("ALL|vi");
		assertThat(PromotionCacheKeys.placements(
				PromotionPlacementKey.HOME_PROMO_BOTTOM,
				PromotionPlacementKey.HOME_HERO
		)).isEqualTo("HOME_HERO,HOME_PROMO_BOTTOM|vi");
	}

	@Test
	void localeSeparatesHomepageAndPlacementsKeys() {
		RequestLocaleHolder.set(AppLocale.VI);
		String homeVi = PromotionCacheKeys.homepage();
		String allVi = PromotionCacheKeys.placements();

		RequestLocaleHolder.set(AppLocale.EN);
		String homeEn = PromotionCacheKeys.homepage();
		String allEn = PromotionCacheKeys.placements();

		assertThat(homeVi).isEqualTo("homepage|vi");
		assertThat(homeEn).isEqualTo("homepage|en");
		assertThat(allVi).isEqualTo("ALL|vi");
		assertThat(allEn).isEqualTo("ALL|en");
	}
}
