package com.aptech.aptech4.promotion.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin upsert for placement title/subtitle/ctaLabel. translations.vi.title required.")
public record PlacementTranslationsUpdateRequest(
		@NotNull
		Map<String, @Valid PlacementLocaleTextRequest> translations
) {
}
