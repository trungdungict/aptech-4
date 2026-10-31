package com.aptech.aptech4.promotion.domain;

/**
 * MVP strategy types (ADR-020). Future types (BUY_X_GET_Y, FREE_SHIPPING) are out of schema for now.
 */
public enum PromotionStrategyType {
	PERCENTAGE_DISCOUNT,
	FIXED_AMOUNT_DISCOUNT,
	PRODUCT_DISCOUNT,
	CATEGORY_DISCOUNT,
	BANNER_ONLY
}
