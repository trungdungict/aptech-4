package com.aptech.aptech4.analytics;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.analytics.api.AdminAnalyticsAttentionResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsCategorySalesItemResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsCategorySalesResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsOverviewResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsPromotionBreakdownItemResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsPromotionSummaryResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsStatusResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsTimeseriesPointResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsTimeseriesResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsTopProductItemResponse;
import com.aptech.aptech4.analytics.api.AdminAnalyticsTopProductsResponse;
import com.aptech.aptech4.catalog.application.AdminInventoryService;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.aptech.aptech4.order.persistence.ShopOrderSpecs;

/**
 * Shared read-only admin analytics (API-independent). Chat tools and Admin REST
 * must call this service so formulas stay single-sourced ({@link AnalyticsMetrics}).
 *
 * <p>Revenue uses {@code order.total_amount} for {@link AnalyticsMetrics#REVENUE_STATUSES}.
 * Top sellers use historical order_item snapshots, not live catalog prices.
 */
@Service
public class AdminAnalyticsService {

	public static final int DEFAULT_LIST_LIMIT = 10;
	public static final int MAX_LIST_LIMIT = 20;

	private static final String TOP_PRODUCTS_NOTE =
			"Quantities and revenue use historical order_item snapshots, not current catalog prices.";
	private static final String CATEGORY_ATTRIBUTION_NOTE =
			"Attribution uses live products.category_id. Historical category is not preserved; "
					+ "lines with null product_id are excluded.";

	/** @deprecated Prefer {@link AnalyticsMetrics#REVENUE_STATUSES}. */
	@Deprecated
	public static final List<OrderStatus> REVENUE_STATUSES = AnalyticsMetrics.REVENUE_STATUSES;

	/** @deprecated Prefer {@link AnalyticsMetrics#ATTENTION_STATUSES}. */
	@Deprecated
	public static final List<OrderStatus> ATTENTION_STATUSES = AnalyticsMetrics.ATTENTION_STATUSES;

	private final ShopOrderRepository shopOrderRepository;
	private final InventoryRepository inventoryRepository;
	private final AdminInventoryService adminInventoryService;
	private final int defaultLowStockThreshold;
	private final int maxToolResults;

	public AdminAnalyticsService(
			ShopOrderRepository shopOrderRepository,
			InventoryRepository inventoryRepository,
			AdminInventoryService adminInventoryService,
			@Value("${aptech.ai.low-stock-threshold:5}") int defaultLowStockThreshold,
			@Value("${aptech.ai.max-tool-results:20}") int maxToolResults
	) {
		this.shopOrderRepository = shopOrderRepository;
		this.inventoryRepository = inventoryRepository;
		this.adminInventoryService = adminInventoryService;
		this.defaultLowStockThreshold = Math.max(0, defaultLowStockThreshold);
		this.maxToolResults = Math.max(1, maxToolResults);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> orderCountsByStatus(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
		for (OrderStatus status : OrderStatus.values()) {
			counts.put(status, 0L);
		}
		for (Object[] row : shopOrderRepository.countGroupedByStatus(range.from(), range.to())) {
			counts.put((OrderStatus) row[0], (Long) row[1]);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("timezone", AnalyticsMetrics.TIMEZONE);
		body.put("counts", counts);
		body.put("total", counts.values().stream().mapToLong(Long::longValue).sum());
		return body;
	}

	/**
	 * Exact current fulfillment-queue counts (all placed dates). Statuses:
	 * {@link AnalyticsMetrics#ATTENTION_STATUSES}.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> attentionCounts() {
		Instant from = Instant.EPOCH;
		Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
		long paid = shopOrderRepository.countByStatuses(List.of(OrderStatus.PAID), from, to);
		long processing = shopOrderRepository.countByStatuses(List.of(OrderStatus.PROCESSING), from, to);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("attentionStatuses", AnalyticsMetrics.ATTENTION_STATUSES.stream().map(Enum::name).toList());
		body.put("attentionDefinition", AnalyticsMetrics.ATTENTION_DEFINITION_COUNTS);
		body.put("paid", paid);
		body.put("processing", processing);
		body.put("totalMatching", paid + processing);
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> ordersRequiringAttention(int limit) {
		int size = clamp(limit);
		Map<String, Object> counts = attentionCounts();
		long totalMatching = ((Number) counts.get("totalMatching")).longValue();
		List<ShopOrder> orders = shopOrderRepository.findByStatusIn(
				AnalyticsMetrics.ATTENTION_STATUSES,
				PageRequest.of(0, size)
		);
		List<Map<String, Object>> items = orders.stream().map(order -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", order.getId());
			row.put("status", order.getStatus().name());
			row.put("totalAmount", order.getTotalAmount());
			row.put("currency", order.getCurrency());
			row.put("placedAt", order.getPlacedAt());
			row.put("path", "/admin/orders/" + order.getId());
			return row;
		}).toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("attentionStatuses", AnalyticsMetrics.ATTENTION_STATUSES.stream().map(Enum::name).toList());
		body.put("attentionDefinition", AnalyticsMetrics.ATTENTION_DEFINITION_LIST);
		body.put("totalMatching", totalMatching);
		body.put("items", items);
		body.put("returned", items.size());
		body.put("pathHint", "/admin/orders");
		body.put(
				"note",
				"Use totalMatching for exact counts. items is a sample page (size=" + size + "), not the full set."
		);
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> salesSummary(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		BigDecimal revenue = shopOrderRepository.sumTotalsByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		long orderCount = shopOrderRepository.countByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		BigDecimal revenueTotal = revenue == null ? BigDecimal.ZERO : revenue;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("timezone", AnalyticsMetrics.TIMEZONE);
		body.put("currency", "VND");
		body.put("revenueStatuses", AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList());
		body.put("revenueDefinition", AnalyticsMetrics.REVENUE_DEFINITION);
		body.put("orderCount", orderCount);
		// coalesce in JPQL already returns 0 — never invent revenue on failure; exceptions propagate.
		body.put("revenueTotal", revenueTotal);
		body.put("empty", orderCount == 0);
		return body;
	}

	/**
	 * Paginated Admin order search by optional status and total-amount bounds (all placed dates by default).
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> searchOrders(
			OrderStatus status,
			BigDecimal minTotal,
			BigDecimal maxTotal,
			Instant from,
			Instant to,
			int limit
	) {
		return searchOrders(status, minTotal, maxTotal, true, true, from, to, limit);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> searchOrders(
			OrderStatus status,
			BigDecimal minTotal,
			BigDecimal maxTotal,
			boolean minInclusive,
			boolean maxInclusive,
			Instant from,
			Instant to,
			int limit
	) {
		if (minTotal != null && maxTotal != null && minTotal.compareTo(maxTotal) > 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "minTotal must be <= maxTotal");
		}
		// List/filter queries default to the full fulfillment history (not the 30-day analytics window).
		AnalyticsDateRange range = AnalyticsDateRange.normalizeList(from, to);
		int size = clamp(limit);
		PageRequest pageable = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "placedAt"));
		Page<ShopOrder> page = shopOrderRepository.findAll(
				ShopOrderSpecs.adminSearch(
						status, minTotal, maxTotal, minInclusive, maxInclusive, range.from(), range.to()
				),
				pageable
		);
		List<Map<String, Object>> items = page.getContent().stream().map(order -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", order.getId());
			row.put("status", order.getStatus().name());
			row.put("totalAmount", order.getTotalAmount());
			row.put("currency", order.getCurrency());
			row.put("placedAt", order.getPlacedAt());
			row.put("path", "/admin/orders/" + order.getId());
			return row;
		}).toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status == null ? null : status.name());
		body.put("minTotal", minTotal);
		body.put("maxTotal", maxTotal);
		body.put("minInclusive", minInclusive);
		body.put("maxInclusive", maxInclusive);
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("timezone", AnalyticsMetrics.TIMEZONE);
		body.put("totalMatching", page.getTotalElements());
		body.put("returned", items.size());
		body.put("pageSize", size);
		body.put("items", items);
		body.put("pathHint", "/admin/orders");
		body.put("empty", page.getTotalElements() == 0);
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> topSellingProducts(Instant from, Instant to, int limit) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		int size = clamp(limit);
		List<Object[]> rows = shopOrderRepository.topSellingProducts(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to(), PageRequest.of(0, size)
		);
		List<Map<String, Object>> items = new ArrayList<>();
		for (Object[] row : rows) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("productSku", row[0]);
			item.put("productName", row[1]);
			item.put("productId", row[2]);
			item.put("unitsSold", row[3]);
			item.put("revenue", row[4]);
			if (row[2] != null) {
				item.put("path", "/admin/products/" + row[2] + "/edit");
			}
			items.add(item);
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("revenueStatuses", AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList());
		body.put("items", items);
		body.put("note", "Quantities and revenue use historical order_item snapshots, not current catalog prices.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> lowStockProducts(Integer threshold, int limit) {
		int thr = threshold == null ? defaultLowStockThreshold : Math.max(0, threshold);
		int size = clamp(limit);
		List<Inventory> rows = inventoryRepository.findActiveLowStock(thr, PageRequest.of(0, size));
		return inventoryList("low_stock", thr, rows, false);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> outOfStockProducts(int limit) {
		int size = clamp(limit);
		List<Inventory> rows = inventoryRepository.findActiveOutOfStock(PageRequest.of(0, size));
		return inventoryList("out_of_stock", 0, rows, true);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> inventoryRiskSummary(Integer threshold, int limit) {
		int thr = threshold == null ? defaultLowStockThreshold : Math.max(0, threshold);
		Map<String, Object> low = lowStockProducts(thr, limit);
		Map<String, Object> out = outOfStockProducts(limit);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("lowStockThreshold", thr);
		body.put("availableDefinition", AnalyticsMetrics.AVAILABLE_DEFINITION);
		body.put("outOfStock", out.get("items"));
		body.put("lowStock", low.get("items"));
		body.put("recommendationNote",
				"Reorder recommendations are heuristic (low available vs recent sales) — not purchase orders.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> reorderRecommendations(Integer threshold, int limit) {
		int thr = threshold == null ? defaultLowStockThreshold : Math.max(0, threshold);
		int size = clamp(limit);
		Instant to = Instant.now();
		Instant from = to.minus(AnalyticsMetrics.DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS);
		List<Inventory> low = inventoryRepository.findActiveLowStock(thr, PageRequest.of(0, size));
		Map<String, Long> soldBySku = new LinkedHashMap<>();
		for (Object[] row : shopOrderRepository.topSellingProducts(
				AnalyticsMetrics.REVENUE_STATUSES, from, to, PageRequest.of(0, 100))) {
			soldBySku.put((String) row[0], ((Number) row[3]).longValue());
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (Inventory inv : low) {
			var product = inv.getProduct();
			long sold30d = soldBySku.getOrDefault(product.getSku(), 0L);
			Map<String, Object> row = inventoryRow(inv);
			row.put("unitsSoldLast30Days", sold30d);
			row.put("priority", sold30d > 0 && inv.getAvailable() <= thr ? "HIGH" : "NORMAL");
			row.put("assumption", "Priority HIGH when sold in last 30 days and available <= threshold " + thr);
			items.add(row);
		}
		items.sort((a, b) -> {
			int p = priorityRank(b.get("priority")).compareTo(priorityRank(a.get("priority")));
			if (p != 0) {
				return p;
			}
			return Long.compare((Long) b.get("unitsSoldLast30Days"), (Long) a.get("unitsSoldLast30Days"));
		});
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", from);
		body.put("to", to);
		body.put("lowStockThreshold", thr);
		body.put("items", items);
		body.put("isEstimate", true);
		body.put("note", "Insufficient movement history beyond order_items aggregates; treat as guidance only.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> cancellationSummary(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		long cancelled = shopOrderRepository.countByStatuses(
				List.of(OrderStatus.CANCELLED), range.from(), range.to()
		);
		long total = shopOrderRepository.countPlacedBetween(range.from(), range.to());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("cancelledCount", cancelled);
		body.put("totalOrdersInRange", total);
		body.put("cancellationRate", total == 0 ? 0.0 : (double) cancelled / (double) total);
		body.put("note", total < 5
				? "Insufficient history for strong pattern claims."
				: "Rate is cancelled/total orders in range; investigate manually for root causes.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsOverviewResponse overview(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		Map<String, Long> statusCounts = zeroFilledStatusCounts(range.from(), range.to());
		long placedOrderCount = statusCounts.values().stream().mapToLong(Long::longValue).sum();
		long revenueOrderCount = shopOrderRepository.countByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		BigDecimal revenueRaw = shopOrderRepository.sumTotalsByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		BigDecimal revenueTotal = revenueRaw == null ? BigDecimal.ZERO : revenueRaw;
		long cancellationCount = statusCounts.getOrDefault(OrderStatus.CANCELLED.name(), 0L);
		double cancellationRate = placedOrderCount == 0
				? 0.0
				: (double) cancellationCount / (double) placedOrderCount;

		Map<String, Object> attentionMap = attentionCounts();
		AdminAnalyticsAttentionResponse attention = new AdminAnalyticsAttentionResponse(
				((Number) attentionMap.get("paid")).longValue(),
				((Number) attentionMap.get("processing")).longValue(),
				((Number) attentionMap.get("totalMatching")).longValue()
		);

		return new AdminAnalyticsOverviewResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				"VND",
				AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList(),
				placedOrderCount,
				revenueOrderCount,
				revenueTotal,
				AnalyticsMetrics.averageOrderValue(revenueTotal, revenueOrderCount),
				cancellationCount,
				cancellationRate,
				statusCounts,
				attention,
				adminInventoryService.summary(),
				placedOrderCount == 0,
				AnalyticsMetrics.REVENUE_DEFINITION
		);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsStatusResponse orderStatusDistribution(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		Map<String, Long> counts = zeroFilledStatusCounts(range.from(), range.to());
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		return new AdminAnalyticsStatusResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				counts,
				total,
				total == 0
		);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsTimeseriesResponse orderTimeseries(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
		for (LocalDate day : utcDaysIntersecting(range.from(), range.to())) {
			byDay.put(day, new long[] {0L, 0L});
		}
		Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();
		for (LocalDate day : byDay.keySet()) {
			revenueByDay.put(day, BigDecimal.ZERO);
		}
		for (Object[] row : shopOrderRepository.dailyPlacedAndRevenue(range.from(), range.to())) {
			LocalDate day = toLocalDate(row[0]);
			if (!byDay.containsKey(day)) {
				continue;
			}
			byDay.get(day)[0] = ((Number) row[1]).longValue();
			revenueByDay.put(day, toBigDecimal(row[2]));
		}
		List<AdminAnalyticsTimeseriesPointResponse> points = new ArrayList<>();
		for (Map.Entry<LocalDate, long[]> entry : byDay.entrySet()) {
			points.add(new AdminAnalyticsTimeseriesPointResponse(
					entry.getKey().toString(),
					entry.getValue()[0],
					revenueByDay.get(entry.getKey())
			));
		}
		long placedInWindow = shopOrderRepository.countPlacedBetween(range.from(), range.to());
		return new AdminAnalyticsTimeseriesResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				"DAY",
				points,
				placedInWindow == 0
		);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsTopProductsResponse topProducts(Instant from, Instant to, int limit) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		int size = clampListLimit(limit);
		List<Object[]> rows = shopOrderRepository.topSellingProducts(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to(), PageRequest.of(0, size)
		);
		List<AdminAnalyticsTopProductItemResponse> items = new ArrayList<>();
		for (Object[] row : rows) {
			UUID productId = (UUID) row[2];
			items.add(new AdminAnalyticsTopProductItemResponse(
					(String) row[0],
					(String) row[1],
					productId,
					((Number) row[3]).longValue(),
					toBigDecimal(row[4]),
					productId == null ? null : "/admin/products/" + productId + "/edit"
			));
		}
		return new AdminAnalyticsTopProductsResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList(),
				items,
				items.isEmpty(),
				TOP_PRODUCTS_NOTE
		);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsCategorySalesResponse categorySales(Instant from, Instant to, int limit) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		int size = clampListLimit(limit);
		List<Object[]> rows = shopOrderRepository.categorySales(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to(), PageRequest.of(0, size)
		);
		List<AdminAnalyticsCategorySalesItemResponse> items = new ArrayList<>();
		for (Object[] row : rows) {
			items.add(new AdminAnalyticsCategorySalesItemResponse(
					(UUID) row[0],
					(String) row[1],
					((Number) row[2]).longValue(),
					toBigDecimal(row[3]),
					((Number) row[4]).longValue()
			));
		}
		return new AdminAnalyticsCategorySalesResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList(),
				items,
				items.isEmpty(),
				CATEGORY_ATTRIBUTION_NOTE
		);
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public AdminAnalyticsPromotionSummaryResponse promotionSummary(Instant from, Instant to) {
		AnalyticsDateRange range = AnalyticsDateRange.normalize(from, to);
		BigDecimal discountRaw = shopOrderRepository.sumDiscountByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		BigDecimal discountTotal = discountRaw == null ? BigDecimal.ZERO : discountRaw;
		long ordersWithPromotionCount = shopOrderRepository.countOrdersWithPromotionSnapshots(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		long revenueOrderCount = shopOrderRepository.countByStatuses(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		);
		List<AdminAnalyticsPromotionBreakdownItemResponse> breakdown = new ArrayList<>();
		for (Object[] row : shopOrderRepository.promotionBreakdown(
				AnalyticsMetrics.REVENUE_STATUSES, range.from(), range.to()
		)) {
			String strategyCode = row[1] == null ? "" : (String) row[1];
			breakdown.add(new AdminAnalyticsPromotionBreakdownItemResponse(
					(String) row[0],
					strategyCode,
					(String) row[2],
					(String) row[3],
					(String) row[4],
					toBigDecimal(row[5]),
					((Number) row[6]).longValue()
			));
		}
		return new AdminAnalyticsPromotionSummaryResponse(
				range.from(),
				range.to(),
				AnalyticsMetrics.TIMEZONE,
				"VND",
				AnalyticsMetrics.REVENUE_STATUSES.stream().map(Enum::name).toList(),
				discountTotal,
				ordersWithPromotionCount,
				revenueOrderCount,
				breakdown,
				ordersWithPromotionCount == 0
		);
	}

	private Map<String, Long> zeroFilledStatusCounts(Instant from, Instant to) {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (OrderStatus status : OrderStatus.values()) {
			counts.put(status.name(), 0L);
		}
		for (Object[] row : shopOrderRepository.countGroupedByStatus(from, to)) {
			counts.put(((OrderStatus) row[0]).name(), (Long) row[1]);
		}
		return counts;
	}

	/**
	 * Contiguous UTC calendar days that intersect {@code [from, to)}.
	 */
	static List<LocalDate> utcDaysIntersecting(Instant from, Instant to) {
		LocalDate first = from.atZone(ZoneOffset.UTC).toLocalDate();
		LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
		Instant toMidnight = toDate.atStartOfDay(ZoneOffset.UTC).toInstant();
		LocalDate last = to.equals(toMidnight) ? toDate.minusDays(1) : toDate;
		List<LocalDate> days = new ArrayList<>();
		if (last.isBefore(first)) {
			return days;
		}
		for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
			days.add(d);
		}
		return days;
	}

	private static LocalDate toLocalDate(Object value) {
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		if (value instanceof Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		if (value instanceof java.util.Date utilDate) {
			return utilDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
		}
		return LocalDate.parse(value.toString());
	}

	private static BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		if (value instanceof BigDecimal bigDecimal) {
			return bigDecimal;
		}
		return new BigDecimal(value.toString());
	}

	private int clampListLimit(int limit) {
		if (limit < 1 || limit > MAX_LIST_LIMIT) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"limit must be between 1 and " + MAX_LIST_LIMIT
			);
		}
		return limit;
	}

	private Map<String, Object> inventoryList(String kind, int threshold, List<Inventory> rows, boolean outOnly) {
		List<Map<String, Object>> items = rows.stream().map(this::inventoryRow).toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("kind", kind);
		body.put("threshold", threshold);
		body.put("availableDefinition", AnalyticsMetrics.AVAILABLE_DEFINITION);
		body.put("outOfStockDefinition", "available <= 0");
		body.put("lowStockDefinition", "0 < available <= threshold (or available <= threshold including zero for low list)");
		body.put("items", items);
		body.put("itemCount", items.size());
		body.put("pathHint", "/admin/products");
		if (outOnly) {
			body.put("filter", "available <= 0");
		}
		if (items.isEmpty()) {
			body.put(
					"message",
					"No matching products in the live catalog for this filter. "
							+ "Tell the user none were found — do not invent SKUs, names, or quantities."
			);
		}
		return body;
	}

	private Map<String, Object> inventoryRow(Inventory inv) {
		var product = inv.getProduct();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("productId", product.getId());
		row.put("sku", product.getSku());
		row.put("name", product.getName());
		row.put("active", product.isActive());
		row.put("categoryId", product.getCategory() != null ? product.getCategory().getId() : null);
		row.put("quantityOnHand", inv.getQuantityOnHand());
		row.put("quantityReserved", inv.getQuantityReserved());
		row.put("available", inv.getAvailable());
		row.put("path", "/admin/products/" + product.getId() + "/edit");
		return row;
	}

	private int clamp(int limit) {
		if (limit <= 0) {
			return Math.min(10, maxToolResults);
		}
		return Math.min(limit, maxToolResults);
	}

	private static Integer priorityRank(Object priority) {
		return "HIGH".equals(priority) ? 2 : 1;
	}
}
