package com.aptech.aptech4.promotion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Backend-owned cart line for promotion pricing (catalog prices already resolved).
 */
public record PricingCartLine(
		UUID productId,
		UUID categoryId,
		int quantity,
		BigDecimal lineTotal
) {
}
