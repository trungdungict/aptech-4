package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Small, testable routing for high-confidence Admin operational questions (EN + VI).
 * Does not override server-side authorization — callers still enforce ROLE_ADMIN.
 */
public final class AdminIntentRouter {

	private static final Pattern ATTENTION = Pattern.compile(
			"need(s|ing)?\\s+processing|requir(e|ing)\\s+attention|awaiting\\s+fulfillment|"
					+ "chờ\\s*xử\\s*lý|can\\s*xu\\s*ly|cần\\s*xử\\s*lý",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern SALES = Pattern.compile(
			"summarize\\s+sales|sales\\s+summary|sales\\s+for(\\s+the)?\\s+last|"
					+ "\\brevenue\\b|doanh\\s*thu|tóm\\s*tắt\\s*doanh\\s*thu",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern COUNTS = Pattern.compile(
			"order\\s+counts?|counts?\\s+by\\s+status|"
					+ "(how\\s+many\\s+orders(?!\\s+need))|"
					+ "bao\\s*nhiêu\\s*đơn(?!\\s*đang\\s*chờ)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern LIST_VERB = Pattern.compile(
			"\\b(list|show|liệt\\s*kê|liet\\s*ke|đơn\\s*hàng\\s*nào|don\\s*hang\\s*nao)\\b|"
					+ "list\\s+up\\s+orders|orders?\\s+that\\s+have\\s+been|"
					+ "orders?\\s+has\\s+been|which\\s+orders",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern PAID_STATUS = Pattern.compile(
			"\\bpaid\\b|đã\\s*thanh\\s*toán|da\\s*thanh\\s*toan|thanh\\s*toán|"
					+ "have\\s+been\\s+paid|has\\s+been\\s+paid",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern PRICE_COMPARE = Pattern.compile(
			"(?:order\\s+)?(?:price|total|amount|giá\\s*trị|gia\\s*tri)\\s*(?:>|>=|≥|over|above|greater\\s+than|more\\s+than|trên|lon\\s*hon|lớn\\s*hơn)\\s*|"
					+ "(?:>|>=|over|above|greater\\s+than|trên)\\s*(?=\\d)|"
					+ "orders?\\s+with\\s+a\\s+total\\s+greater\\s+than|"
					+ "orders?\\s+over\\b|"
					+ "đơn\\s*hàng\\s*có\\s*giá\\s*trị\\s*trên|"
					+ "don\\s*hang\\s*co\\s*gia\\s*tri\\s*tren",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern PRICE_LT = Pattern.compile(
			"(?:order\\s+)?(?:price|total|amount|giá\\s*trị)\\s*(?:<|<=|≤|under|below|less\\s+than|dưới)|"
					+ "(?:<|<=|under|below|less\\s+than|dưới)\\s*(?=\\d)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern USD_MARK = Pattern.compile("\\$|\\bUSD\\b|\\bdollars?\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern VND_MARK = Pattern.compile(
			"\\bVND\\b|đồng|dong|₫",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern NUMBER = Pattern.compile(
			"(\\d{1,3}(?:[.,]\\d{3})+|\\d+(?:[.,]\\d+)?)"
	);

	private AdminIntentRouter() {
	}

	public static AdminIntentMatch route(String message) {
		if (message == null || message.isBlank()) {
			return AdminIntentMatch.none();
		}
		String text = message.toLowerCase(Locale.ROOT);

		AdminInventoryQueryKind inventory = AdminInventoryQueryKind.detect(message);
		if (inventory != AdminInventoryQueryKind.NONE) {
			return switch (inventory) {
				case LOW_STOCK -> AdminIntentMatch.of(AdminAnalyticsQueryKind.INVENTORY_LOW);
				case OUT_OF_STOCK -> AdminIntentMatch.of(AdminAnalyticsQueryKind.INVENTORY_OUT);
				case RISK_SUMMARY -> AdminIntentMatch.of(AdminAnalyticsQueryKind.INVENTORY_RISK);
				case NONE -> AdminIntentMatch.none();
			};
		}

		if (ATTENTION.matcher(text).find()) {
			return AdminIntentMatch.of(AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION);
		}

		boolean hasUsd = USD_MARK.matcher(message).find();
		boolean hasVnd = VND_MARK.matcher(message).find();
		boolean priceIntent = PRICE_COMPARE.matcher(text).find() || PRICE_LT.matcher(text).find()
				|| text.matches("(?s).*order\\s+price\\s*[><=].*")
				|| text.contains("order price");

		if (priceIntent) {
			if (hasUsd && !hasVnd) {
				return AdminIntentMatch.clarify(
						"Order totals in this store are in VND. Did you mean a VND amount (for example 320000 VND), or a USD amount to convert?",
						"Tổng đơn trong cửa hàng dùng VND. Bạn muốn lọc theo số tiền VND (ví dụ 320000 VND), hay số USD cần quy đổi?"
				);
			}
			Optional<BigDecimal> amount = parseAmount(message, hasVnd);
			if (amount.isEmpty()) {
				return AdminIntentMatch.clarify(
						"I could not safely parse the price filter. Please use a clear amount such as 320000 or 320,000 VND.",
						"Không đọc được mức giá một cách an toàn. Hãy dùng số rõ ràng như 320000 hoặc 320.000 VND."
				);
			}
			boolean less = PRICE_LT.matcher(text).find() && !PRICE_COMPARE.matcher(text).find();
			boolean inclusiveBound = text.contains(">=") || text.contains("<=") || text.contains("at least")
					|| text.contains("at most") || text.contains("tối thiểu") || text.contains("tối đa");
			if (less) {
				// `<` / under → exclusive max unless `<=` / at most
				return AdminIntentMatch.listByPrice(null, amount.get(), true, inclusiveBound);
			}
			// `>` / over / greater than / trên → exclusive min unless `>=` / at least
			return AdminIntentMatch.listByPrice(amount.get(), null, inclusiveBound, true);
		}

		boolean listIntent = LIST_VERB.matcher(text).find()
				|| (PAID_STATUS.matcher(text).find() && (text.contains("order") || text.contains("đơn") || text.contains("don")));
		if (listIntent && PAID_STATUS.matcher(text).find()) {
			return AdminIntentMatch.listByStatus(OrderStatus.PAID);
		}
		if (listIntent && !COUNTS.matcher(text).find()) {
			OrderStatus status = detectListedStatus(text);
			if (status != null) {
				return AdminIntentMatch.listByStatus(status);
			}
		}

		if (SALES.matcher(text).find()) {
			return AdminIntentMatch.of(AdminAnalyticsQueryKind.SALES_SUMMARY);
		}
		if (COUNTS.matcher(text).find()) {
			return AdminIntentMatch.of(AdminAnalyticsQueryKind.ORDER_COUNTS_BY_STATUS);
		}
		return AdminIntentMatch.none();
	}

	private static OrderStatus detectListedStatus(String text) {
		if (PAID_STATUS.matcher(text).find()) {
			return OrderStatus.PAID;
		}
		if (text.contains("processing") || text.contains("đang xử lý")) {
			return OrderStatus.PROCESSING;
		}
		if (text.contains("shipped") || text.contains("đã giao vận") || text.contains("đang giao")) {
			return OrderStatus.SHIPPED;
		}
		if (text.contains("completed") || text.contains("hoàn thành")) {
			return OrderStatus.COMPLETED;
		}
		if (text.contains("cancelled") || text.contains("canceled") || text.contains("đã hủy")) {
			return OrderStatus.CANCELLED;
		}
		return null;
	}

	/**
	 * Parses a money amount from user text.
	 * <ul>
	 *   <li>Plain digits: as-is</li>
	 *   <li>EN thousands {@code 320,000}: 320000</li>
	 *   <li>VI thousands {@code 320.000} (exactly 3 fraction digits) when VND context: 320000</li>
	 *   <li>Two-decimal {@code 320.00}: 320.00</li>
	 * </ul>
	 */
	static Optional<BigDecimal> parseAmount(String message, boolean vndContext) {
		Matcher matcher = NUMBER.matcher(message.replace('₫', ' '));
		if (!matcher.find()) {
			return Optional.empty();
		}
		String raw = matcher.group(1);
		try {
			if (raw.matches("\\d{1,3}(,\\d{3})+")) {
				return Optional.of(new BigDecimal(raw.replace(",", "")));
			}
			if (raw.matches("\\d{1,3}(\\.\\d{3})+") && (vndContext || raw.matches("\\d{1,3}(\\.\\d{3})+"))) {
				// Vietnamese thousands separators: 320.000 → 320000
				if (raw.chars().filter(ch -> ch == '.').count() >= 1
						&& raw.substring(raw.lastIndexOf('.') + 1).length() == 3
						&& !raw.matches("\\d+\\.\\d{1,2}")) {
					return Optional.of(new BigDecimal(raw.replace(".", "")));
				}
			}
			if (raw.matches("\\d+,\\d{1,2}")) {
				return Optional.of(new BigDecimal(raw.replace(',', '.')));
			}
			if (raw.matches("\\d+\\.\\d{1,2}")) {
				return Optional.of(new BigDecimal(raw));
			}
			if (raw.matches("\\d+")) {
				return Optional.of(new BigDecimal(raw));
			}
			// Ambiguous mixed separators
			return Optional.empty();
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
	}
}
