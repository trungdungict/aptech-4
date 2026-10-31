package com.aptech.aptech4.promotion.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "PromotionCampaignWriteRequest")
public record PromotionCampaignWriteRequest(
		@NotNull UUID strategyId,
		@NotBlank @Size(max = 160) String name,
		@Size(max = 160) String slug,
		@NotNull Instant startsAt,
		@NotNull Instant endsAt,
		Integer priority,
		Boolean stackable,
		String notes,
		List<UUID> productIds,
		List<UUID> categoryIds
) {
}
