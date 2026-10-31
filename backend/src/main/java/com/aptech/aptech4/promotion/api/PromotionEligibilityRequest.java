package com.aptech.aptech4.promotion.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionEligibilityRequest(
		@NotNull List<UUID> productIds,
		@NotNull List<UUID> categoryIds
) {
}
