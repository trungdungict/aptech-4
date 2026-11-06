package com.aptech.aptech4.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One locale's editable catalog text (product/category).
 */
public record TranslationLocaleTextRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 10000) String description
) {
}
