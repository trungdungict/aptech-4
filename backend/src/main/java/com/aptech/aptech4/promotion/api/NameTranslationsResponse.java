package com.aptech.aptech4.promotion.api;

import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin bilingual name map for strategy/campaign. Keys vi|en; missing EN may be null.")
public record NameTranslationsResponse(
		UUID id,
		Map<String, String> translations
) {
}
