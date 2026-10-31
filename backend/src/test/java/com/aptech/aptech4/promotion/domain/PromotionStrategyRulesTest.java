package com.aptech.aptech4.promotion.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.common.api.ApiException;

class PromotionStrategyRulesTest {

	@Test
	void bannerOnlyRejectsDiscountValues() {
		assertThatThrownBy(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.BANNER_ONLY,
				new BigDecimal("10"),
				null,
				null,
				null,
				null
		)).isInstanceOf(ApiException.class)
				.hasMessageContaining("BANNER_ONLY");

		assertThatCode(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.BANNER_ONLY, null, null, null, null, null
		)).doesNotThrowAnyException();
	}

	@Test
	void percentageRequiresPercentOnly() {
		assertThatThrownBy(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.PERCENTAGE_DISCOUNT, null, null, null, null, null
		)).isInstanceOf(ApiException.class);

		assertThatCode(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.PERCENTAGE_DISCOUNT, new BigDecimal("20"), null, null, null, null
		)).doesNotThrowAnyException();
	}

	@Test
	void productDiscountRequiresXor() {
		assertThatThrownBy(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.PRODUCT_DISCOUNT, null, null, null, null, null
		)).isInstanceOf(ApiException.class);

		assertThatThrownBy(() -> PromotionStrategyRules.validateWrite(
				PromotionStrategyType.PRODUCT_DISCOUNT,
				new BigDecimal("10"),
				new BigDecimal("5"),
				null,
				null,
				null
		)).isInstanceOf(ApiException.class);
	}
}
