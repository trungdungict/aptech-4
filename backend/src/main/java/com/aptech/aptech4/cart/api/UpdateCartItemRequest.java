package com.aptech.aptech4.cart.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateCartItemRequest(
		@NotNull
		@Min(1)
		@Max(CartConstraints.MAX_QUANTITY)
		Integer quantity
) {
}
