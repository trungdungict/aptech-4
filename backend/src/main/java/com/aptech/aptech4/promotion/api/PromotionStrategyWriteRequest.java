package com.aptech.aptech4.promotion.api;

import java.math.BigDecimal;

import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "PromotionStrategyWriteRequest")
public record PromotionStrategyWriteRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 64) String code,
		@NotNull PromotionStrategyType type,
		BigDecimal discountPercent,
		BigDecimal discountAmount,
		BigDecimal maxDiscountAmount,
		BigDecimal minOrderAmount,
		Integer minQuantity,
		Boolean stackable,
		Integer priority,
		Boolean active
) {
}
