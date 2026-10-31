package com.aptech.aptech4.promotion.domain;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Server-side strategy value validation (ADR-020 / AC-E5).
 */
public final class PromotionStrategyRules {

	private PromotionStrategyRules() {
	}

	public static void validateWrite(
			PromotionStrategyType type,
			BigDecimal discountPercent,
			BigDecimal discountAmount,
			BigDecimal maxDiscountAmount,
			BigDecimal minOrderAmount,
			Integer minQuantity
	) {
		if (type == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Strategy type is required");
		}
		requireNonNegative("discountPercent", discountPercent);
		requireNonNegative("discountAmount", discountAmount);
		requireNonNegative("maxDiscountAmount", maxDiscountAmount);
		requireNonNegative("minOrderAmount", minOrderAmount);
		if (minQuantity != null && minQuantity < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "minQuantity must be zero or greater");
		}
		if (discountPercent != null && discountPercent.compareTo(new BigDecimal("100")) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "discountPercent must be at most 100");
		}

		if (type == PromotionStrategyType.BANNER_ONLY) {
			if (discountPercent != null || discountAmount != null || maxDiscountAmount != null) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"Bad Request",
						"BANNER_ONLY strategies cannot define discount values"
				);
			}
			return;
		}

		boolean hasPercent = discountPercent != null;
		boolean hasAmount = discountAmount != null;
		if (type == PromotionStrategyType.PERCENTAGE_DISCOUNT) {
			if (!hasPercent) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "discountPercent is required");
			}
			if (hasAmount) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Use discountPercent only for PERCENTAGE_DISCOUNT");
			}
			return;
		}
		if (type == PromotionStrategyType.FIXED_AMOUNT_DISCOUNT) {
			if (!hasAmount) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "discountAmount is required");
			}
			if (hasPercent) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Use discountAmount only for FIXED_AMOUNT_DISCOUNT");
			}
			return;
		}
		// PRODUCT / CATEGORY: XOR percent vs amount
		if (hasPercent == hasAmount) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Provide exactly one of discountPercent or discountAmount"
			);
		}
	}

	private static void requireNonNegative(String field, BigDecimal value) {
		if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", field + " must be zero or greater");
		}
	}
}
