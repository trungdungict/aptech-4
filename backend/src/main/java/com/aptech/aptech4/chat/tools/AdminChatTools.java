package com.aptech.aptech4.chat.tools;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.aptech.aptech4.chat.application.AdminAnalyticsService;
import com.aptech.aptech4.chat.application.AdminInventoryFacade;
import com.aptech.aptech4.chat.application.ChatToolEnvelope;
import com.aptech.aptech4.chat.application.ChatToolJson;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Admin-only read tools. Inventory tools return {@link ChatToolEnvelope} JSON so empty
 * success is never confused with failure. Analytics service enforces {@code ROLE_ADMIN}.
 */
@Component
public class AdminChatTools {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

	private final AdminAnalyticsService adminAnalyticsService;
	private final AdminInventoryFacade adminInventoryFacade;

	public AdminChatTools(AdminAnalyticsService adminAnalyticsService, AdminInventoryFacade adminInventoryFacade) {
		this.adminAnalyticsService = adminAnalyticsService;
		this.adminInventoryFacade = adminInventoryFacade;
	}

	@Tool(description = "Count orders grouped by status. Prefer omitting from/to so the server uses the last 30 days of real data.")
	public String getOrderCountsByStatus(
			@ToolParam(description = "Optional range start (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String from,
			@ToolParam(description = "Optional range end (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String to
	) {
		return safeAnalytics("getOrderCountsByStatus", () -> adminAnalyticsService.orderCountsByStatus(parseInstant(from), parseInstant(to)));
	}

	@Tool(description = "List orders in PAID or PROCESSING that typically need fulfillment attention.")
	public String getOrdersRequiringAttention(
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		return safeAnalytics("getOrdersRequiringAttention", () -> adminAnalyticsService.ordersRequiringAttention(limit == null ? 10 : limit));
	}

	@Tool(description = "Search/list Admin orders by optional status and totalAmount bounds (VND). Use for 'list paid orders' or 'orders over 320000'. Returns totalMatching + a bounded page of id/status/total/currency/placedAt. Never invent rows.")
	public String searchOrders(
			@ToolParam(description = "Optional OrderStatus enum name, e.g. PAID", required = false) String status,
			@ToolParam(description = "Minimum totalAmount inclusive (VND)", required = false) Double minTotal,
			@ToolParam(description = "Maximum totalAmount inclusive (VND)", required = false) Double maxTotal,
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		return safeAnalytics("searchOrders", () -> adminAnalyticsService.searchOrders(
				parseStatus(status),
				minTotal == null ? null : BigDecimal.valueOf(minTotal),
				maxTotal == null ? null : BigDecimal.valueOf(maxTotal),
				null,
				null,
				limit == null ? 10 : limit
		));
	}

	@Tool(description = "Sales summary: orderCount and revenueTotal for PAID/PROCESSING/SHIPPED/COMPLETED. Omit from/to for last 30 days.")
	public String getSalesSummary(
			@ToolParam(description = "Optional range start (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String from,
			@ToolParam(description = "Optional range end (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String to
	) {
		return safeAnalytics("getSalesSummary", () -> adminAnalyticsService.salesSummary(parseInstant(from), parseInstant(to)));
	}

	@Tool(description = "Top selling products by units sold using historical order_item snapshots. Omit from/to for last 30 days.")
	public String getTopSellingProducts(
			@ToolParam(description = "Optional range start (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String from,
			@ToolParam(description = "Optional range end (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String to,
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		return safeAnalytics("getTopSellingProducts", () -> adminAnalyticsService.topSellingProducts(
				parseInstant(from), parseInstant(to), limit == null ? 10 : limit
		));
	}

	@Tool(description = "List active products whose available stock (on_hand - reserved) is at or below a threshold. Returns envelope status SUCCESS_EMPTY|SUCCESS_NONEMPTY|FAILURE. Never invent rows when empty or failed.")
	public String getLowStockProducts(
			@ToolParam(description = "Available threshold; omit for configured low-stock threshold (do not invent a large threshold)", required = false) Integer threshold,
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		return ChatToolJson.write(OBJECT_MAPPER, adminInventoryFacade.lowStock(threshold, limit));
	}

	@Tool(description = "List active products with available stock <= 0. Returns envelope status SUCCESS_EMPTY|SUCCESS_NONEMPTY|FAILURE.")
	public String getOutOfStockProducts(
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		return ChatToolJson.write(OBJECT_MAPPER, adminInventoryFacade.outOfStock(limit));
	}

	@Tool(description = "Combined inventory risk view: out-of-stock and low-stock lists. Returns envelope status SUCCESS_EMPTY|SUCCESS_NONEMPTY|FAILURE.")
	public String getInventoryRiskSummary(
			@ToolParam(description = "Low-stock available threshold", required = false) Integer threshold,
			@ToolParam(description = "Max results per list", required = false) Integer limit
	) {
		return ChatToolJson.write(OBJECT_MAPPER, adminInventoryFacade.riskSummary(threshold, limit));
	}

	@Tool(description = "Heuristic reorder priorities from low stock plus last-30-day sales. Marked as estimate.")
	public String getReorderRecommendations(
			@ToolParam(description = "Low-stock available threshold", required = false) Integer threshold,
			@ToolParam(description = "Max results", required = false) Integer limit
	) {
		return safeAnalytics("getReorderRecommendations", () -> adminAnalyticsService.reorderRecommendations(
				threshold, limit == null ? 10 : limit
		));
	}

	@Tool(description = "Cancelled order count and rate. Omit from/to for last 30 days.")
	public String getCancellationSummary(
			@ToolParam(description = "Optional range start (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String from,
			@ToolParam(description = "Optional range end (ISO-8601 Instant or yyyy-MM-dd). Omit for last-30-days default.", required = false) String to
	) {
		return safeAnalytics("getCancellationSummary", () -> adminAnalyticsService.cancellationSummary(
				parseInstant(from), parseInstant(to)
		));
	}

	private String safeAnalytics(String tool, ThrowingSupplier<Map<String, Object>> supplier) {
		long started = System.nanoTime();
		try {
			Map<String, Object> result = supplier.get();
			long ms = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
			int size = result.containsKey("items") && result.get("items") instanceof java.util.List<?> list
					? list.size()
					: 1;
			return ChatToolJson.write(OBJECT_MAPPER, ChatToolEnvelope.success(tool, result, size, ms));
		}
		catch (AccessDeniedException ex) {
			long ms = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
			return ChatToolJson.write(
					OBJECT_MAPPER,
					ChatToolEnvelope.failure(tool, ChatToolEnvelope.ERROR_AUTHORIZATION, "ADMIN role required", ms)
			);
		}
		catch (ApiException ex) {
			long ms = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
			String category = ex.getStatus() == HttpStatus.BAD_REQUEST
					? ChatToolEnvelope.ERROR_VALIDATION
					: ChatToolEnvelope.ERROR_DATA_ACCESS;
			return ChatToolJson.write(OBJECT_MAPPER, ChatToolEnvelope.failure(tool, category, ex.getMessage(), ms));
		}
		catch (RuntimeException ex) {
			long ms = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
			return ChatToolJson.write(
					OBJECT_MAPPER,
					ChatToolEnvelope.failure(tool, ChatToolEnvelope.ERROR_INTERNAL, "Analytics query failed", ms)
			);
		}
	}

	/**
	 * Accepts ISO-8601 Instants or {@code yyyy-MM-dd} dates. Invalid values become
	 * {@code null} so analytics fall back to the last-30-days default instead of failing
	 * the whole turn (llama3.2 often invents date-only strings).
	 */
	static Instant parseInstant(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		try {
			return Instant.parse(value);
		}
		catch (DateTimeParseException ignored) {
			// fall through
		}
		try {
			LocalDate date = LocalDate.parse(value);
			return date.atStartOfDay().toInstant(ZoneOffset.UTC);
		}
		catch (DateTimeParseException ignored) {
			return null;
		}
	}

	static OrderStatus parseStatus(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return OrderStatus.valueOf(raw.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unknown order status: " + raw);
		}
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get();
	}
}
