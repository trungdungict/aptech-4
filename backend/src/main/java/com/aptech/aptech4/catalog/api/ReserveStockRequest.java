package com.aptech.aptech4.catalog.api;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReserveStockRequest(
		@NotBlank
		@Size(max = 100)
		String idempotencyKey,

		@NotEmpty
		@Size(max = 50)
		List<@Valid @NotNull ReserveStockLineRequest> items
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ReserveStockLineRequest(
			@NotNull
			UUID productId,

			@NotNull
			@Min(1)
			@Max(99)
			Integer quantity
	) {
	}
}
