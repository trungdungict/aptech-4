package com.aptech.aptech4.promotion.api;

import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "PromotionPlacementWriteRequest")
public record PromotionPlacementWriteRequest(
		@NotNull PromotionPlacementKey placementKey,
		@NotBlank @Size(max = 160) String title,
		@Size(max = 255) String subtitle,
		@Size(max = 80) String ctaLabel,
		PromotionCtaType ctaType,
		UUID ctaProductId,
		UUID ctaCategoryId,
		@Size(max = 80) String ctaCollectionKey,
		@Size(max = 512) String ctaUrl,
		@NotBlank @Size(max = 512) String desktopImageUrl,
		@Size(max = 512) String mobileImageUrl,
		Integer displayOrder,
		Instant startsAt,
		Instant endsAt,
		Boolean enabled
) {
}
