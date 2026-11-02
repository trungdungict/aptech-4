package com.aptech.aptech4.analytics.api;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.analytics.AdminAnalyticsService;
import com.aptech.aptech4.common.api.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(
		name = "Admin Analytics",
		description = "Admin analytics reads (ROLE_ADMIN). Windows use placed_at >= from AND placed_at < to (UTC)."
)
public class AdminAnalyticsController {

	private final AdminAnalyticsService adminAnalyticsService;

	public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
		this.adminAnalyticsService = adminAnalyticsService;
	}

	@GetMapping("/overview")
	@Operation(
			summary = "Analytics overview KPIs",
			description = """
					Revenue/AOV for REVENUE_STATUSES (PAID/PROCESSING/SHIPPED/COMPLETED); cancellation rate; \
					zero-filled status counts; live attention queue; embeds Admin inventory summary \
					(aptech.inventory.low-stock-threshold). Default window last 30 days; max 366 days.
					"""
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Overview",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsOverviewResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsOverviewResponse> overview(
			@Parameter(description = "Inclusive start Instant (ISO-8601 UTC)")
			@RequestParam(required = false) Instant from,
			@Parameter(description = "Exclusive end Instant (ISO-8601 UTC)")
			@RequestParam(required = false) Instant to
	) {
		return ResponseEntity.ok(adminAnalyticsService.overview(from, to));
	}

	@GetMapping("/orders/status")
	@Operation(
			summary = "Order counts by status",
			description = "Zero-fills every OrderStatus for the normalized placed_at window."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Status distribution",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsStatusResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsStatusResponse> orderStatus(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to
	) {
		return ResponseEntity.ok(adminAnalyticsService.orderStatusDistribution(from, to));
	}

	@GetMapping("/orders/timeseries")
	@Operation(
			summary = "Daily placed-order count and revenue",
			description = """
					UTC day buckets zero-filled across the window. placedOrderCount counts all statuses; \
					revenueTotal sums total_amount for REVENUE_STATUSES only.
					"""
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Timeseries",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsTimeseriesResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsTimeseriesResponse> orderTimeseries(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to
	) {
		return ResponseEntity.ok(adminAnalyticsService.orderTimeseries(from, to));
	}

	@GetMapping("/products/top")
	@Operation(
			summary = "Top selling products",
			description = "Groups order_item snapshots by sku/name/productId for REVENUE_STATUSES in the window."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Top products",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsTopProductsResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range or limit",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsTopProductsResponse> topProducts(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@Parameter(description = "Max rows (1–20, default 10)")
			@RequestParam(required = false, defaultValue = "" + AdminAnalyticsService.DEFAULT_LIST_LIMIT) int limit
	) {
		return ResponseEntity.ok(adminAnalyticsService.topProducts(from, to, limit));
	}

	@GetMapping("/categories/sales")
	@Operation(
			summary = "Sales by live product category",
			description = """
					Joins order_items → products.category_id (live). Null product_id lines excluded. \
					Historical category attribution is not preserved.
					"""
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Category sales",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsCategorySalesResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range or limit",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsCategorySalesResponse> categorySales(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@Parameter(description = "Max rows (1–20, default 10)")
			@RequestParam(required = false, defaultValue = "" + AdminAnalyticsService.DEFAULT_LIST_LIMIT) int limit
	) {
		return ResponseEntity.ok(adminAnalyticsService.categorySales(from, to, limit));
	}

	@GetMapping("/promotions/summary")
	@Operation(
			summary = "Promotion discount summary",
			description = """
					discountTotal = SUM(orders.discount_amount) for REVENUE_STATUSES in window. \
					Usage and breakdown use order_promotion_snapshots (survives campaign delete).
					"""
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Promotion summary",
					content = @Content(schema = @Schema(implementation = AdminAnalyticsPromotionSummaryResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid date range",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AdminAnalyticsPromotionSummaryResponse> promotionSummary(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to
	) {
		return ResponseEntity.ok(adminAnalyticsService.promotionSummary(from, to));
	}
}
