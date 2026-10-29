package com.aptech.aptech4.order.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.order.application.OrderService;
import com.aptech.aptech4.order.domain.OrderStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Orders", description = "Customer order history (ADR-008). Snapshots only — not live catalog prices.")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	@Operation(summary = "List my orders",
			description = "Zero-based page. Default size=20, max=100. Sort whitelist: placedAt, totalAmount, status.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of orders"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<PageResponse<OrderResponse>> list(
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + OrderService.DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort
	) {
		return ResponseEntity.ok(orderService.listMine(status, page, size, sort));
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "Get my order detail")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found",
					content = @Content(schema = @Schema(implementation = OrderResponse.class))),
			@ApiResponse(responseCode = "404", description = "Missing or not owned",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<OrderResponse> get(@PathVariable UUID orderId) {
		return ResponseEntity.ok(orderService.getMine(orderId));
	}

	@PostMapping("/{orderId}/cancel")
	@Operation(summary = "Cancel my order",
			description = "Only PENDING_PAYMENT → CANCELLED (ADR-008). Paid orders cannot be cancelled here.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cancelled"),
			@ApiResponse(responseCode = "404", description = "Missing or not owned"),
			@ApiResponse(responseCode = "409", description = "Invalid status for cancel")
	})
	public ResponseEntity<OrderResponse> cancel(@PathVariable UUID orderId) {
		return ResponseEntity.ok(orderService.cancelMine(orderId));
	}
}
