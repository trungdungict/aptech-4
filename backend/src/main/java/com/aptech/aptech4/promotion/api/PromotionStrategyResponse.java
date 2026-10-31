package com.aptech.aptech4.promotion.api;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PromotionStrategyResponse")
public record PromotionStrategyResponse(
		UUID id,
		String name,
		String code,
		PromotionStrategyType type,
		BigDecimal discountPercent,
		BigDecimal discountAmount,
		BigDecimal maxDiscountAmount,
		BigDecimal minOrderAmount,
		Integer minQuantity,
		boolean stackable,
		int priority,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static PromotionStrategyResponse from(PromotionStrategy strategy) {
		return new PromotionStrategyResponse(
				strategy.getId(),
				strategy.getName(),
				strategy.getCode(),
				strategy.getType(),
				strategy.getDiscountPercent(),
				strategy.getDiscountAmount(),
				strategy.getMaxDiscountAmount(),
				strategy.getMinOrderAmount(),
				strategy.getMinQuantity(),
				strategy.isStackable(),
				strategy.getPriority(),
				strategy.isActive(),
				strategy.getCreatedAt(),
				strategy.getUpdatedAt()
		);
	}
}
