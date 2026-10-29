package com.aptech.aptech4.order.api;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutRequest(
		@NotBlank
		@Size(max = 100)
		String idempotencyKey,

		@NotNull
		UUID addressId,

		/**
		 * Honored only when {@code aptech.payment.mock.allow-client-force-failure=true}.
		 * Clients cannot set payment success; success is decided by the mock gateway.
		 */
		Boolean simulateFailure
) {
}
