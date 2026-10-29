package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.aptech.aptech4.order.persistence.ShopOrderSpecs;

/**
 * Read-only admin analytics. Revenue uses order line snapshots for statuses that
 * imply a successful mock payment (PAID and later fulfillment states).
 */
@Service
public class AdminAnalyticsService {

	/** Statuses counted as paid revenue (mock gateway SUCCEEDED → order PAID). */
	public static final List<OrderStatus> REVENUE_STATUSES = List.of(
			OrderStatus.PAID,
			OrderStatus.PROCESSING,
			OrderStatus.SHIPPED,
			OrderStatus.COMPLETED
	);

	public static final List<OrderStatus> ATTENTION_STATUSES = List.of(
			OrderStatus.PAID,
			OrderStatus.PROCESSING
	);

	private final ShopOrderRepository shopOrderRepository;
	private final InventoryRepository inventoryRepository;
	private final AiProperties aiProperties;

	public AdminAnalyticsService(
			ShopOrderRepository shopOrderRepository,
			InventoryRepository inventoryRepository,
			AiProperties aiProperties
	) {
		this.shopOrderRepository = shopOrderRepository;
		this.inventoryRepository = inventoryRepository;
		this.aiProperties = aiProperties;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> orderCountsByStatus(Instant from, Instant to) {
		Range range = normalizeRange(from, to);
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
		body.put("timezone", "UTC");
		body.put("counts", counts);
		body.put("total", counts.values().stream().mapToLong(Long::longValue).sum());
		return body;
	}

	/**
	 * Exact current fulfillment-queue counts (all placed dates). Statuses: {@link #ATTENTION_STATUSES}.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> attentionCounts() {
		Instant from = Instant.EPOCH;
		Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
		long paid = shopOrderRepository.countByStatuses(List.of(OrderStatus.PAID), from, to);
		long processing = shopOrderRepository.countByStatuses(List.of(OrderStatus.PROCESSING), from, to);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("attentionStatuses", ATTENTION_STATUSES.stream().map(Enum::name).toList());
		body.put("attentionDefinition", "Orders in PAID or PROCESSING (fulfillment queue, all dates)");
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
				ATTENTION_STATUSES,
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
		body.put("attentionStatuses", ATTENTION_STATUSES.stream().map(Enum::name).toList());
		body.put("attentionDefinition", "Orders in PAID or PROCESSING (fulfillment queue)");
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
		Range range = normalizeRange(from, to);
		BigDecimal revenue = shopOrderRepository.sumTotalsByStatuses(
				REVENUE_STATUSES, range.from(), range.to()
		);
		long orderCount = shopOrderRepository.countByStatuses(
				REVENUE_STATUSES, range.from(), range.to()
		);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", range.from());
		body.put("to", range.to());
		body.put("timezone", "UTC");
		body.put("currency", "VND");
		body.put("revenueStatuses", REVENUE_STATUSES.stream().map(Enum::name).toList());
		body.put("revenueDefinition",
				"Sum of order.total_amount for statuses PAID/PROCESSING/SHIPPED/COMPLETED "
						+ "(mock payment succeeded). Excludes PENDING_PAYMENT, CANCELLED, FAILED.");
		body.put("orderCount", orderCount);
		// coalesce in JPQL already returns 0 — never invent revenue on failure; exceptions propagate.
		body.put("revenueTotal", revenue == null ? BigDecimal.ZERO : revenue);
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
		Range range = normalizeListRange(from, to);
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
		body.put("timezone", "UTC");
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
		Range range = normalizeRange(from, to);
		int size = clamp(limit);
		List<Object[]> rows = shopOrderRepository.topSellingProducts(
				REVENUE_STATUSES, range.from(), range.to(), PageRequest.of(0, size)
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
		body.put("revenueStatuses", REVENUE_STATUSES.stream().map(Enum::name).toList());
		body.put("items", items);
		body.put("note", "Quantities and revenue use historical order_item snapshots, not current catalog prices.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> lowStockProducts(Integer threshold, int limit) {
		int thr = threshold == null ? aiProperties.getLowStockThreshold() : Math.max(0, threshold);
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
		int thr = threshold == null ? aiProperties.getLowStockThreshold() : Math.max(0, threshold);
		Map<String, Object> low = lowStockProducts(thr, limit);
		Map<String, Object> out = outOfStockProducts(limit);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("lowStockThreshold", thr);
		body.put("availableDefinition", "available = quantity_on_hand - quantity_reserved");
		body.put("outOfStock", out.get("items"));
		body.put("lowStock", low.get("items"));
		body.put("recommendationNote",
				"Reorder recommendations are heuristic (low available vs recent sales) — not purchase orders.");
		return body;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Map<String, Object> reorderRecommendations(Integer threshold, int limit) {
		int thr = threshold == null ? aiProperties.getLowStockThreshold() : Math.max(0, threshold);
		int size = clamp(limit);
		Instant to = Instant.now();
		Instant from = to.minus(30, ChronoUnit.DAYS);
		List<Inventory> low = inventoryRepository.findActiveLowStock(thr, PageRequest.of(0, size));
		Map<String, Long> soldBySku = new LinkedHashMap<>();
		for (Object[] row : shopOrderRepository.topSellingProducts(
				REVENUE_STATUSES, from, to, PageRequest.of(0, 100))) {
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
		Range range = normalizeRange(from, to);
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

	private Map<String, Object> inventoryList(String kind, int threshold, List<Inventory> rows, boolean outOnly) {
		List<Map<String, Object>> items = rows.stream().map(this::inventoryRow).toList();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("kind", kind);
		body.put("threshold", threshold);
		body.put("availableDefinition", "available = quantity_on_hand - quantity_reserved");
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
		int max = aiProperties.getMaxToolResults();
		if (limit <= 0) {
			return Math.min(10, max);
		}
		return Math.min(limit, max);
	}

	private static Integer priorityRank(Object priority) {
		return "HIGH".equals(priority) ? 2 : 1;
	}

	private Range normalizeRange(Instant from, Instant to) {
		Instant end = to == null ? Instant.now() : to;
		Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
		if (!start.isBefore(end)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "from must be before to");
		}
		if (ChronoUnit.DAYS.between(start, end) > 366) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Date range must be at most 366 days");
		}
		return new Range(start, end);
	}

	/** Full-history window for order list/price filters (bounded only by from &lt; to). */
	private Range normalizeListRange(Instant from, Instant to) {
		Instant end = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to;
		Instant start = from == null ? Instant.EPOCH : from;
		if (!start.isBefore(end)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "from must be before to");
		}
		return new Range(start, end);
	}

	private record Range(Instant from, Instant to) {
	}
}
