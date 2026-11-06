package com.aptech.aptech4.catalog.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin upsert for vi/en translations. vi.name is required; en is optional.")
public record EntityTranslationsUpdateRequest(
		@NotNull
		Map<String, @Valid TranslationLocaleTextRequest> translations
) {
}
