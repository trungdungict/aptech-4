package com.aptech.aptech4.catalog.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.catalog.application.AdminInventoryService;
import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(
		name = "Admin Inventory",
		description = "Admin inventory list/summary (ROLE_ADMIN). Absolute set-on-hand remains under /admin/products/{id}/inventory."
)
public class AdminInventoryController {

	private final AdminInventoryService adminInventoryService;

	public AdminInventoryController(AdminInventoryService adminInventoryService) {
		this.adminInventoryService = adminInventoryService;
	}

	@GetMapping
	@Operation(
			summary = "List inventory for all catalog products",
			description = """
					Includes active and inactive products. Missing inventory rows report hasInventoryRow=false \
					and zero quantities (available=0). stockStatus uses configured aptech.inventory.low-stock-threshold. \
					Sort whitelist: name, sku, active, createdAt, quantityOnHand, quantityReserved, updatedAt.
					"""
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of inventory rows"),
			@ApiResponse(responseCode = "400", description = "Invalid page/size/sort/stockStatus",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<PageResponse<AdminInventoryListItemResponse>> list(
			@Parameter(description = "Search name or SKU (case-insensitive contains)")
			@RequestParam(required = false) String q,
			@Parameter(description = "Filter by product.active")
			@RequestParam(required = false) Boolean active,
			@Parameter(description = "ALL (default), IN_STOCK, LOW, or OUT")
			@RequestParam(required = false, defaultValue = "ALL") AdminInventoryStockStatus stockStatus,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + AdminInventoryService.DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort
	) {
		return ResponseEntity.ok(adminInventoryService.list(q, active, stockStatus, page, size, sort));
	}

	@GetMapping("/summary")
	@Operation(
			summary = "Inventory summary aggregates",
			description = "Catalog-wide counts using the same available / LOW / OUT definitions as the list endpoint."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Summary",
					content = @Content(schema = @Schema(implementation = AdminInventorySummaryResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminInventorySummaryResponse> summary() {
		return ResponseEntity.ok(adminInventoryService.summary());
	}
}
