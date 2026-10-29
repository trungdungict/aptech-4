package com.aptech.aptech4.order.api;

import com.aptech.aptech4.order.domain.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderStatusUpdateRequest(
		@NotNull
		OrderStatus status
) {
}
