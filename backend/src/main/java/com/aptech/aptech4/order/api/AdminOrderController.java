package com.aptech.aptech4.order.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin Orders", description = "Admin order list/detail and fulfillment status (ROLE_ADMIN, ADR-008)")
public class AdminOrderController {

	private final OrderService orderService;

	public AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	@Operation(summary = "List all orders",
			description = "Optional status filter. Pagination/sort same as customer list.")
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
		return ResponseEntity.ok(orderService.listAdmin(status, page, size, sort));
	}

	@GetMapping("/{orderId}")
	@Operation(summary = "Get any order detail")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found"),
			@ApiResponse(responseCode = "404", description = "Not found")
	})
	public ResponseEntity<OrderResponse> get(@PathVariable UUID orderId) {
		return ResponseEntity.ok(orderService.getAdmin(orderId));
	}

	@PatchMapping("/{orderId}/status")
	@Operation(summary = "Update fulfillment status",
			description = """
					Whitelist transitions only (ADR-008). Does not change payment, totals, items, or owner.
					Ignores unknown JSON fields. Cannot set PAID/FAILED (checkout-owned).
					""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated",
					content = @Content(schema = @Schema(implementation = OrderResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "409", description = "Invalid transition",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<OrderResponse> updateStatus(
			@PathVariable UUID orderId,
			@Valid @RequestBody OrderStatusUpdateRequest request
	) {
		return ResponseEntity.ok(orderService.updateAdminStatus(orderId, request));
	}
}
