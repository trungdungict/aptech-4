package com.aptech.aptech4.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductUpdateRequest(
		@NotBlank
		@Size(max = 64)
		String sku,

		@NotBlank
		@Size(max = 200)
		String name,

		@Size(max = 10_000)
		String description,

		@NotNull
		@DecimalMin(value = "0.00", inclusive = true)
		@Digits(integer = 10, fraction = 2)
		BigDecimal unitPrice,

		@Size(min = 3, max = 3)
		String currency,

		@NotNull
		UUID categoryId,

		@Size(max = 512)
		String imageUrl
) {
}
