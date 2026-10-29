package com.aptech.aptech4.order.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.order.application.CheckoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/checkout")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Checkout", description = "Cart checkout with mock payment (ADR-007). Not a real payment gateway.")
public class CheckoutController {

	private final CheckoutService checkoutService;

	public CheckoutController(CheckoutService checkoutService) {
		this.checkoutService = checkoutService;
	}

	@PostMapping
	@Operation(summary = "Checkout current cart",
			description = """
					Reserves stock (ADR-006), creates order with price snapshots, runs mock payment, then confirms or releases.
					Idempotent per user+idempotencyKey. Cart cleared only on successful payment.
					Clients cannot set paymentStatus; simulateFailure works only when mock force-failure is enabled.
					""")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Checkout completed (PAID or FAILED recorded)",
					content = @Content(schema = @Schema(implementation = CheckoutResponse.class))),
			@ApiResponse(responseCode = "400", description = "Empty cart / unavailable products",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Address not found / not owned"),
			@ApiResponse(responseCode = "409", description = "Insufficient stock or idempotency conflict",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.checkout(request));
	}
}
