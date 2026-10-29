package com.aptech.aptech4.chat.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.chat.api.ChatLanguage;
import com.aptech.aptech4.chat.api.ChatTiming;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Answers high-confidence Admin operational questions from verified service data — no free-form LLM numbers.
 */
@Service
public class AdminDeterministicAnalyticsService {

	private static final Logger log = LoggerFactory.getLogger(AdminDeterministicAnalyticsService.class);

	private final AdminAnalyticsService adminAnalyticsService;
	private final AdminInventoryFacade adminInventoryFacade;

	public AdminDeterministicAnalyticsService(
			AdminAnalyticsService adminAnalyticsService,
			AdminInventoryFacade adminInventoryFacade
	) {
		this.adminAnalyticsService = adminAnalyticsService;
		this.adminInventoryFacade = adminInventoryFacade;
	}

	public record DeterministicAnswer(String reply, ChatTiming timing) {
	}

	public DeterministicAnswer answer(AdminIntentMatch match, ChatLanguage language) {
		if (match == null || match.kind() == AdminAnalyticsQueryKind.NONE) {
			return null;
		}
		long started = System.nanoTime();
		try {
			return switch (match.kind()) {
				case ORDERS_NEEDING_ATTENTION -> attention(language, started);
				case ORDER_COUNTS_BY_STATUS -> orderCounts(language, started);
				case ORDER_LIST_BY_STATUS -> orderList(match, language, started);
				case ORDER_LIST_BY_PRICE -> orderList(match, language, started);
				case SALES_SUMMARY -> sales(language, started);
				case INVENTORY_LOW -> inventory(AdminInventoryQueryKind.LOW_STOCK, language, started);
				case INVENTORY_OUT -> inventory(AdminInventoryQueryKind.OUT_OF_STOCK, language, started);
				case INVENTORY_RISK -> inventory(AdminInventoryQueryKind.RISK_SUMMARY, language, started);
				case CLARIFY -> clarify(match, language, started);
				case NONE -> null;
			};
		}
		catch (AccessDeniedException ex) {
			long ms = elapsedMs(started);
			log.warn("deterministic_admin status=FAILURE category=AUTHORIZATION durationMs={}", ms);
			boolean vi = language == ChatLanguage.VI;
			return new DeterministicAnswer(
					vi ? "Bạn không có quyền Admin để xem phân tích này."
							: "You are not authorized as Admin to view this analytics result.",
					new ChatTiming(ms, 0L, ms, "deterministic_auth_denied")
			);
		}
		catch (ApiException ex) {
			long ms = elapsedMs(started);
			log.warn("deterministic_admin status=FAILURE category=API durationMs={} detail={}", ms, ex.getMessage());
			boolean vi = language == ChatLanguage.VI;
			return new DeterministicAnswer(
					vi ? "Không lấy được dữ liệu phân tích. Không suy diễn số liệu."
							: "Could not load analytics data. No figures were invented.",
					new ChatTiming(ms, 0L, ms, "deterministic_failure")
			);
		}
		catch (RuntimeException ex) {
			long ms = elapsedMs(started);
			log.error("deterministic_admin status=FAILURE category=INTERNAL durationMs={}", ms, ex);
			boolean vi = language == ChatLanguage.VI;
			return new DeterministicAnswer(
					vi ? "Truy vấn phân tích thất bại. Không suy diễn số liệu."
							: "Analytics query failed. No figures were invented.",
					new ChatTiming(ms, 0L, ms, "deterministic_failure")
			);
		}
	}

	/** @deprecated use {@link #answer(AdminIntentMatch, ChatLanguage)} */
	@Deprecated
	public DeterministicAnswer answer(AdminAnalyticsQueryKind kind, ChatLanguage language) {
		return answer(AdminIntentMatch.of(kind), language);
	}

	private DeterministicAnswer clarify(AdminIntentMatch match, ChatLanguage language, long started) {
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		String reply = vi
				? (match.clarifyMessageVi() == null ? "Vui lòng làm rõ câu hỏi." : match.clarifyMessageVi())
				: (match.clarifyMessageEn() == null ? "Please clarify your question." : match.clarifyMessageEn());
		log.info("deterministic_admin kind=CLARIFY durationMs={}", ms);
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_clarify"));
	}

	private DeterministicAnswer attention(ChatLanguage language, long started) {
		Map<String, Object> counts = adminAnalyticsService.attentionCounts();
		long paid = ((Number) counts.get("paid")).longValue();
		long processing = ((Number) counts.get("processing")).longValue();
		long total = ((Number) counts.get("totalMatching")).longValue();
		long ms = elapsedMs(started);
		log.info(
				"deterministic_admin tool=attentionCounts kind=ORDERS_NEEDING_ATTENTION paid={} processing={} total={} durationMs={}",
				paid,
				processing,
				total,
				ms
		);
		boolean vi = language == ChatLanguage.VI;
		String reply = vi
				? total + " đơn cần xử lý: " + paid + " PAID và " + processing + " PROCESSING."
				: total + " orders need processing: " + paid + " PAID and " + processing + " PROCESSING.";
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_orders_attention"));
	}

	@SuppressWarnings("unchecked")
	private DeterministicAnswer orderCounts(ChatLanguage language, long started) {
		Map<String, Object> countsBody = adminAnalyticsService.orderCountsByStatus(null, null);
		Map<OrderStatus, Long> counts = normalizeCounts(countsBody.get("counts"));
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		StringBuilder sb = new StringBuilder(vi ? "Số đơn theo trạng thái (30 ngày gần đây):\n" : "Order counts by status (last 30 days):\n");
		for (OrderStatus status : OrderStatus.values()) {
			sb.append("- ").append(status.name()).append(": ").append(counts.getOrDefault(status, 0L)).append('\n');
		}
		sb.append(vi ? "Tổng: " : "Total: ").append(countsBody.get("total"));
		log.info("deterministic_admin tool=orderCountsByStatus kind=ORDER_COUNTS durationMs={}", ms);
		return new DeterministicAnswer(sb.toString().trim(), new ChatTiming(ms, 0L, ms, "deterministic_order_counts"));
	}

	private DeterministicAnswer sales(ChatLanguage language, long started) {
		Map<String, Object> summary = adminAnalyticsService.salesSummary(null, null);
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		Object orderCount = summary.get("orderCount");
		Object revenue = summary.get("revenueTotal");
		Object currency = summary.get("currency");
		Object from = summary.get("from");
		Object to = summary.get("to");
		boolean empty = Boolean.TRUE.equals(summary.get("empty"));
		String reply;
		if (empty) {
			reply = vi
					? "Không có doanh thu trong khoảng 30 ngày gần đây (không có đơn PAID/PROCESSING/SHIPPED/COMPLETED)."
					: "No sales revenue in the last 30 days (no PAID/PROCESSING/SHIPPED/COMPLETED orders).";
		}
		else {
			reply = (vi ? "Tóm tắt doanh thu (30 ngày gần đây, UTC):\n" : "Sales summary (last 30 days, UTC):\n")
					+ (vi ? "- Khoảng: " : "- Range: ") + from + " → " + to + '\n'
					+ (vi ? "- Số đơn tính doanh thu: " : "- Revenue orders: ") + orderCount + '\n'
					+ (vi ? "- Doanh thu: " : "- Revenue: ") + revenue + ' ' + currency + '\n'
					+ (vi ? "- Trạng thái: " : "- Statuses: ") + summary.get("revenueStatuses");
		}
		log.info(
				"deterministic_admin tool=getSalesSummary orderCount={} revenue={} durationMs={}",
				orderCount,
				revenue,
				ms
		);
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_sales"));
	}

	@SuppressWarnings("unchecked")
	private DeterministicAnswer orderList(AdminIntentMatch match, ChatLanguage language, long started) {
		Map<String, Object> body = adminAnalyticsService.searchOrders(
				match.status(),
				match.minTotal(),
				match.maxTotal(),
				match.minInclusive(),
				match.maxInclusive(),
				null,
				null,
				10
		);
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		long total = ((Number) body.get("totalMatching")).longValue();
		List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
		String filterDesc = describeFilter(match, vi);
		String path = match.kind() == AdminAnalyticsQueryKind.ORDER_LIST_BY_PRICE
				? "deterministic_orders_by_price"
				: "deterministic_orders_by_status";
		log.info(
				"deterministic_admin tool=searchOrders kind={} status={} minTotal={} maxTotal={} totalMatching={} durationMs={}",
				match.kind(),
				match.status(),
				match.minTotal(),
				match.maxTotal(),
				total,
				ms
		);
		if (total == 0) {
			String reply = vi
					? "Không có đơn nào khớp bộ lọc (" + filterDesc + "). Không suy diễn danh sách."
					: "No orders match this filter (" + filterDesc + "). No list was invented.";
			return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, path));
		}
		StringBuilder sb = new StringBuilder();
		sb.append(vi ? "Tìm thấy " : "Found ").append(total).append(vi ? " đơn (" : " orders (")
				.append(filterDesc).append(vi ? "). Hiển thị " : "). Showing ")
				.append(items.size()).append(vi ? " dòng đầu:\n" : " rows:\n");
		int i = 1;
		for (Map<String, Object> row : items) {
			sb.append(i++).append(". ")
					.append(row.get("id")).append(" — ")
					.append(row.get("status")).append(" — ")
					.append(row.get("totalAmount")).append(' ').append(row.get("currency"))
					.append(" — ").append(row.get("placedAt"))
					.append('\n');
		}
		sb.append(vi ? "Chi tiết: /admin/orders" : "Details: /admin/orders");
		return new DeterministicAnswer(sb.toString().trim(), new ChatTiming(ms, 0L, ms, path));
	}

	private static String describeFilter(AdminIntentMatch match, boolean vi) {
		if (match.kind() == AdminAnalyticsQueryKind.ORDER_LIST_BY_STATUS && match.status() != null) {
			return vi ? "trạng thái " + match.status().name() : "status " + match.status().name();
		}
		StringBuilder sb = new StringBuilder();
		if (match.minTotal() != null) {
			String op = match.minInclusive() ? "≥" : ">";
			sb.append(vi ? "tổng " : "total ").append(op).append(' ').append(match.minTotal());
		}
		if (match.maxTotal() != null) {
			if (!sb.isEmpty()) {
				sb.append(", ");
			}
			String op = match.maxInclusive() ? "≤" : "<";
			sb.append(vi ? "tổng " : "total ").append(op).append(' ').append(match.maxTotal());
		}
		if (sb.isEmpty()) {
			return vi ? "không lọc" : "no filter";
		}
		sb.append(vi ? " VND" : " VND");
		return sb.toString();
	}

	private DeterministicAnswer inventory(AdminInventoryQueryKind kind, ChatLanguage language, long started) {
		Map<String, Object> envelope = adminInventoryFacade.execute(kind);
		long ms = elapsedMs(started);
		Object toolMs = envelope.get("durationMs");
		long reportedTool = toolMs instanceof Number n ? n.longValue() : ms;
		String reply = AdminInventoryReplyFormatter.format(kind, envelope, language);
		return new DeterministicAnswer(
				reply,
				new ChatTiming(reportedTool, 0L, ms, "deterministic_inventory")
		);
	}

	@SuppressWarnings("unchecked")
	private static Map<OrderStatus, Long> normalizeCounts(Object raw) {
		Map<OrderStatus, Long> out = new EnumMap<>(OrderStatus.class);
		for (OrderStatus status : OrderStatus.values()) {
			out.put(status, 0L);
		}
		if (!(raw instanceof Map<?, ?> map)) {
			return out;
		}
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			OrderStatus status = null;
			if (entry.getKey() instanceof OrderStatus os) {
				status = os;
			}
			else if (entry.getKey() != null) {
				try {
					status = OrderStatus.valueOf(entry.getKey().toString());
				}
				catch (IllegalArgumentException ignored) {
					continue;
				}
			}
			if (status == null) {
				continue;
			}
			Object value = entry.getValue();
			long count = value instanceof Number n ? n.longValue() : 0L;
			out.put(status, count);
		}
		return out;
	}

	private static long elapsedMs(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}
}
