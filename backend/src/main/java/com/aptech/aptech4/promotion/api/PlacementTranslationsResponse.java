package com.aptech.aptech4.promotion.api;

import java.util.Map;
import java.util.UUID;

import com.aptech.aptech4.promotion.application.PromotionLocalizationService.PlacementText;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin bilingual placement copy. Keys vi|en.")
public record PlacementTranslationsResponse(
		UUID id,
		Map<String, PlacementText> translations
) {
}
