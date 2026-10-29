package com.aptech.aptech4.chat.application;

import java.util.List;
import java.util.Map;

import com.aptech.aptech4.chat.api.ChatLanguage;

/**
 * Renders verified inventory facts from structured analytics maps — no LLM involvement.
 */
public final class AdminInventoryReplyFormatter {

	private AdminInventoryReplyFormatter() {
	}

	@SuppressWarnings("unchecked")
	public static String format(
			AdminInventoryQueryKind kind,
			Map<String, Object> envelope,
			ChatLanguage language
	) {
		boolean vi = language == ChatLanguage.VI;
		String status = String.valueOf(envelope.get("status"));
		if (ChatToolEnvelope.FAILURE.equals(status)) {
			String detail = String.valueOf(envelope.getOrDefault("error", "unknown"));
			return vi
					? "Không lấy được dữ liệu tồn kho (" + envelope.get("errorCategory") + "): " + detail
					: "Could not load inventory data (" + envelope.get("errorCategory") + "): " + detail;
		}

		Object resultObj = envelope.get("result");
		if (!(resultObj instanceof Map<?, ?> raw)) {
			return vi ? "Không có dữ liệu tồn kho hợp lệ." : "No valid inventory data returned.";
		}
		Map<String, Object> result = (Map<String, Object>) raw;

		return switch (kind) {
			case LOW_STOCK -> formatList(
					vi ? "Sản phẩm tồn thấp" : "Low-stock products",
					result,
					vi
			);
			case OUT_OF_STOCK -> formatList(
					vi ? "Sản phẩm hết hàng" : "Out-of-stock products",
					result,
					vi
			);
			case RISK_SUMMARY -> formatRisk(result, vi);
			case NONE -> vi ? "Không nhận diện được câu hỏi tồn kho." : "Inventory intent was not recognized.";
		};
	}

	@SuppressWarnings("unchecked")
	private static String formatList(String title, Map<String, Object> result, boolean vi) {
		Object threshold = result.get("threshold");
		Object def = result.get("availableDefinition");
		List<Map<String, Object>> items = castItems(result.get("items"));
		StringBuilder sb = new StringBuilder();
		sb.append(title);
		if (threshold != null) {
			sb.append(vi ? " (ngưỡng available ≤ " : " (available ≤ ").append(threshold).append(')');
		}
		sb.append('\n');
		if (def != null) {
			sb.append(vi ? "Định nghĩa: " : "Definition: ").append(def).append('\n');
		}
		if (items.isEmpty()) {
			sb.append(vi
					? "Không có sản phẩm nào khớp bộ lọc trong catalog đang hoạt động. Không có SKU/số lượng nào được suy diễn."
					: "No products match this filter in the live active catalog. No SKUs or quantities were invented.");
			return sb.toString();
		}
		sb.append(vi ? "Số dòng: " : "Rows: ").append(items.size()).append('\n');
		int i = 1;
		for (Map<String, Object> row : items) {
			sb.append(i++).append(". ")
					.append(row.get("name")).append(" (SKU ").append(row.get("sku")).append(") — ")
					.append("on_hand=").append(row.get("quantityOnHand"))
					.append(", reserved=").append(row.get("quantityReserved"))
					.append(", available=").append(row.get("available"))
					.append('\n');
		}
		return sb.toString().trim();
	}

	@SuppressWarnings("unchecked")
	private static String formatRisk(Map<String, Object> result, boolean vi) {
		Object thr = result.get("lowStockThreshold");
		Object def = result.get("availableDefinition");
		List<Map<String, Object>> low = castItems(result.get("lowStock"));
		List<Map<String, Object>> out = castItems(result.get("outOfStock"));
		StringBuilder sb = new StringBuilder();
		sb.append(vi ? "Tóm tắt rủi ro tồn kho" : "Inventory risk summary");
		if (thr != null) {
			sb.append(vi ? " (ngưỡng low-stock=" : " (low-stock threshold=").append(thr).append(')');
		}
		sb.append('\n');
		if (def != null) {
			sb.append(vi ? "Định nghĩa: " : "Definition: ").append(def).append('\n');
		}
		sb.append(vi ? "Hết hàng: " : "Out of stock: ").append(out.size()).append('\n');
		appendRows(sb, out);
		sb.append(vi ? "Tồn thấp: " : "Low stock: ").append(low.size()).append('\n');
		appendRows(sb, low);
		if (out.isEmpty() && low.isEmpty()) {
			sb.append(vi
					? "Không có sản phẩm hết hàng hoặc tồn thấp theo ngưỡng hiện tại."
					: "No out-of-stock or low-stock products at the current threshold.");
		}
		return sb.toString().trim();
	}

	private static void appendRows(StringBuilder sb, List<Map<String, Object>> items) {
		int i = 1;
		for (Map<String, Object> row : items) {
			sb.append("  ").append(i++).append(". ")
					.append(row.get("name")).append(" (SKU ").append(row.get("sku")).append(") — available=")
					.append(row.get("available")).append('\n');
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> castItems(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(Map.class::isInstance)
				.map(item -> (Map<String, Object>) item)
				.toList();
	}
}
