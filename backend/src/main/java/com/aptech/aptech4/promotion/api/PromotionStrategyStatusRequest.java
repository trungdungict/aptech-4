package com.aptech.aptech4.promotion.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PromotionStrategyStatusRequest(@NotNull Boolean active) {
}
