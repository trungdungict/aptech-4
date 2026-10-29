package com.aptech.aptech4.cart.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddCartItemRequest(
		@NotNull
		UUID productId,

		@NotNull
		@Min(1)
		@Max(CartConstraints.MAX_QUANTITY)
		Integer quantity
) {
}
