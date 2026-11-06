package com.aptech.aptech4.catalog.api;

import java.util.Map;
import java.util.UUID;

import com.aptech.aptech4.common.locale.LocalizedText;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin bilingual translation map. Keys are locale codes vi|en. Missing EN may have null name.")
public record EntityTranslationsResponse(
		UUID id,
		Map<String, LocalizedText> translations
) {
}
