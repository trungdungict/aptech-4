package com.aptech.aptech4.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Frozen metric definitions shared by chat tools and (future) Admin Analytics REST.
 * Source of truth for P28 — do not diverge formulas between callers.
 *
 * <p><b>Window:</b> {@code placed_at >= from AND placed_at < to}, timezone label {@value #TIMEZONE}.
 * Default analytics window: last {@value #DEFAULT_WINDOW_DAYS} days when from/to omitted.
 *
 * <p><b>Revenue statuses:</b> PAID, PROCESSING, SHIPPED, COMPLETED (mock payment succeeded).
 * Excludes PENDING_PAYMENT, CANCELLED, FAILED.
 *
 * <p><b>AOV:</b> {@code revenueTotal / orderCount} with scale 2 HALF_UP; 0 when count is 0.
 * Not currently embedded in chat {@code salesSummary} payload — available for REST (P29).
 *
 * <p><b>Top sellers:</b> order_item quantity/lineTotal snapshots filtered by parent order revenue statuses.
 *
 * <p><b>Inventory (chat tools):</b> still uses {@code aptech.ai.low-stock-threshold} for low-stock lists.
 * Admin inventory UI/summary uses {@code aptech.inventory.low-stock-threshold} (P26) — do not conflate.
 */
public final class AnalyticsMetrics {

	public static final String TIMEZONE = "UTC";

	public static final int DEFAULT_WINDOW_DAYS = 30;

	public static final int MAX_RANGE_DAYS = 366;

	/** Statuses counted as paid revenue (mock gateway SUCCEEDED → order PAID). */
	public static final List<OrderStatus> REVENUE_STATUSES = List.of(
			OrderStatus.PAID,
			OrderStatus.PROCESSING,
			OrderStatus.SHIPPED,
			OrderStatus.COMPLETED
	);

	/** Fulfillment-queue statuses for attention lists/counts (all placed dates). */
	public static final List<OrderStatus> ATTENTION_STATUSES = List.of(
			OrderStatus.PAID,
			OrderStatus.PROCESSING
	);

	public static final String REVENUE_DEFINITION =
			"Sum of order.total_amount for statuses PAID/PROCESSING/SHIPPED/COMPLETED "
					+ "(mock payment succeeded). Excludes PENDING_PAYMENT, CANCELLED, FAILED.";

	public static final String AVAILABLE_DEFINITION =
			"available = quantity_on_hand - quantity_reserved";

	public static final String ATTENTION_DEFINITION_COUNTS =
			"Orders in PAID or PROCESSING (fulfillment queue, all dates)";

	public static final String ATTENTION_DEFINITION_LIST =
			"Orders in PAID or PROCESSING (fulfillment queue)";

	private AnalyticsMetrics() {
	}

	/**
	 * Average order value for revenue orders. Never invents non-zero when count is 0.
	 */
	public static BigDecimal averageOrderValue(BigDecimal revenueTotal, long revenueOrderCount) {
		if (revenueOrderCount <= 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal revenue = revenueTotal == null ? BigDecimal.ZERO : revenueTotal;
		return revenue.divide(BigDecimal.valueOf(revenueOrderCount), 2, RoundingMode.HALF_UP);
	}
}
