package com.aptech.aptech4.promotion.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlacementLocaleTextRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 500) String subtitle,
		@Size(max = 100) String ctaLabel
) {
}
