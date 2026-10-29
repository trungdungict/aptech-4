package com.aptech.aptech4.catalog.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.catalog.application.InventoryReservationService;
import com.aptech.aptech4.common.api.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/inventory/reservations")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Inventory Reservations", description = "Soft stock holds (ADR-006). Confirm is service-only for checkout (P06-T03).")
public class InventoryReservationController {

	private final InventoryReservationService reservationService;

	public InventoryReservationController(InventoryReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	@Operation(summary = "Create a stock reservation",
			description = "Holds available stock until TTL expiry, release, or checkout confirm. "
					+ "Idempotent per user+idempotencyKey. Does not create orders or take payment.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Reserved (or prior idempotent result)"),
			@ApiResponse(responseCode = "400", description = "Validation / product unavailable",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Insufficient stock",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<StockReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.reserve(request));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get own reservation")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found"),
			@ApiResponse(responseCode = "404", description = "Missing or not owned")
	})
	public ResponseEntity<StockReservationResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(reservationService.getOwned(id));
	}

	@PostMapping("/{id}/release")
	@Operation(summary = "Release own reservation (idempotent)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Released or already terminal"),
			@ApiResponse(responseCode = "404", description = "Missing or not owned")
	})
	public ResponseEntity<StockReservationResponse> release(@PathVariable UUID id) {
		return ResponseEntity.ok(reservationService.releaseOwned(id));
	}
}
