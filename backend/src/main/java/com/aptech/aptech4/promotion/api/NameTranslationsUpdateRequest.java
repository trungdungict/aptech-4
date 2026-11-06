package com.aptech.aptech4.promotion.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin upsert for vi/en display names. translations.vi is required and non-blank.")
public record NameTranslationsUpdateRequest(
		@NotNull
		Map<String, String> translations
) {
}
