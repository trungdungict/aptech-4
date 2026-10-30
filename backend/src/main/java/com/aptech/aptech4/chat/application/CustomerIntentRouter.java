package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Small Customer intent router for high-confidence operational questions (EN + VI).
 */
public final class CustomerIntentRouter {

	private static final Pattern LATEST_ORDER = Pattern.compile(
			"latest\\s+order|most\\s+recent\\s+order|last\\s+order|"
					+ "status\\s+(and\\s+total\\s+amount\\s+)?of\\s+my\\s+latest|"
					+ "đơn\\s*hàng\\s*gần\\s*nhất|don\\s*hang\\s*gan\\s*nhat|"
					+ "trạng\\s*thái\\s*đơn\\s*hàng\\s*gần\\s*nhất",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	/** Store-wide / Admin analytics phrasing — Customer must get a permission message, not a JWT prompt. */
	private static final Pattern ADMIN_STORE_ANALYTICS = Pattern.compile(
			"across\\s+the\\s+(entire\\s+)?store|store[- ]wide|entire\\s+store|"
					+ "all\\s+customers|orders\\s+need(?:ing)?\\s+processing|"
					+ "currently\\s+being\\s+processed|"
					+ "how\\s+many\\s+orders\\s+are\\s+currently|"
					+ "toàn\\s*cửa\\s*hàng|toàn\\s*hệ\\s*thống",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern ORDER_COUNT_BY_STATUS = Pattern.compile(
			"how\\s+many\\s+(?<status>paid|processing|completed|shipped|cancelled|failed|pending(?:\\s+payment)?)\\s+orders|"
					+ "(?<status2>paid|processing|completed|shipped|cancelled|failed)\\s+order\\s+count|"
					+ "bao\\s*nhiêu\\s*đơn\\s*(?<statusVi>đã\\s*thanh\\s*toán|đang\\s*xử\\s*lý|hoàn\\s*thành)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern ORDER_LIST_BY_STATUS = Pattern.compile(
			"(list|show|display)\\s+(me\\s+)?(my\\s+)?(?<status>paid|processing|completed|shipped|cancelled|failed|pending(?:\\s+payment)?)\\s+orders|"
					+ "my\\s+(?<status2>paid|processing|completed|shipped|cancelled|failed)\\s+orders|"
					+ "liệt\\s*kê\\s*đơn\\s*(?<statusVi>đã\\s*thanh\\s*toán|đang\\s*xử\\s*lý|hoàn\\s*thành)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	/**
	 * Customer-scoped total order count only.
	 * Keep after {@link #ORDER_COUNT_BY_STATUS} / admin analytics so status-qualified and
	 * store-wide prompts are not stolen. Avoid matching Admin "How many orders need processing?".
	 */
	private static final Pattern ORDER_COUNT = Pattern.compile(
			"how\\s+many\\s+orders\\s+(have|did)\\s+i|"
					+ "how\\s+many\\s+orders\\s+do\\s+i\\s+have|"
					+ "how\\s+many\\s+orders\\s+i\\s+have|"
					+ "orders?\\s+have\\s+i\\s+placed|"
					+ "how\\s+many\\s+orders\\s+have\\s+i\\s+placed|"
					+ "total\\s+number\\s+of\\s+my\\s+orders?|"
					+ "number\\s+of\\s+(my\\s+)?orders?\\s+(i\\s+)?(have|placed)|"
					+ "my\\s+(total\\s+)?orders?\\s+total|"
					+ "my\\s+(total\\s+)?order\\s+count|"
					+ "count\\s+(of\\s+)?my\\s+orders|"
					+ "số\\s*đơn\\s*hàng|"
					+ "tổng\\s*số\\s*đơn(\\s*hàng)?|"
					+ "đếm\\s*đơn(\\s*hàng)?|"
					+ "tôi\\s*đã\\s*đặt\\s*bao\\s*nhiêu|"
					+ "bao\\s*nhiêu\\s*đơn\\s*hàng\\s*(của\\s*tôi)?|"
					+ "tôi\\s*có\\s*bao\\s*nhiêu\\s*đơn",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern CHECKOUT = Pattern.compile(
			"complete\\s+checkout|how\\s+do\\s+i\\s+checkout|checkout\\s+help|"
					+ "thanh\\s*toán|checkout\\s+thế\\s+nào|tôi\\s+checkout",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern PRODUCTS_UNDER = Pattern.compile(
			"(available\\s+)?products?\\s+under|"
					+ "show\\s+available\\s+products|"
					+ "products?\\s+(below|cheaper\\s+than|less\\s+than)|"
					+ "sản\\s*phẩm.*(dưới|under)|"
					+ "tìm\\s*sản\\s*phẩm",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private static final Pattern NUMBER = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})+|\\d+)");

	private CustomerIntentRouter() {
	}

	public static CustomerIntentMatch route(String message) {
		if (message == null || message.isBlank()) {
			return CustomerIntentMatch.none();
		}
		String text = message.toLowerCase(Locale.ROOT);

		if (ADMIN_STORE_ANALYTICS.matcher(text).find()) {
			return CustomerIntentMatch.of(CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN);
		}

		Matcher countByStatus = ORDER_COUNT_BY_STATUS.matcher(text);
		if (countByStatus.find()) {
			Optional<OrderStatus> status = parseStatusToken(
					firstNonBlank(countByStatus.group("status"), countByStatus.group("status2"), countByStatus.group("statusVi"))
			);
			if (status.isPresent()) {
				return CustomerIntentMatch.countByStatus(status.get());
			}
		}

		Matcher listByStatus = ORDER_LIST_BY_STATUS.matcher(text);
		if (listByStatus.find()) {
			Optional<OrderStatus> status = parseStatusToken(
					firstNonBlank(listByStatus.group("status"), listByStatus.group("status2"), listByStatus.group("statusVi"))
			);
			if (status.isPresent()) {
				return CustomerIntentMatch.listByStatus(status.get());
			}
		}

		if (ORDER_COUNT.matcher(text).find()) {
			return CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT);
		}
		if (LATEST_ORDER.matcher(text).find()) {
			return CustomerIntentMatch.of(CustomerIntentKind.LATEST_ORDER);
		}
		if (CHECKOUT.matcher(text).find()) {
			return CustomerIntentMatch.of(CustomerIntentKind.CHECKOUT_HELP);
		}
		if (PRODUCTS_UNDER.matcher(text).find()) {
			Optional<BigDecimal> max = parseMaxPrice(message);
			return max.map(CustomerIntentMatch::productsUnder)
					.orElseGet(() -> CustomerIntentMatch.productsUnder(new BigDecimal("2000000")));
		}
		return CustomerIntentMatch.none();
	}

	static Optional<BigDecimal> parseMaxPrice(String message) {
		Matcher matcher = NUMBER.matcher(message);
		if (!matcher.find()) {
			return Optional.empty();
		}
		String raw = matcher.group(1);
		try {
			if (raw.matches("\\d{1,3}(,\\d{3})+")) {
				return Optional.of(new BigDecimal(raw.replace(",", "")));
			}
			if (raw.matches("\\d{1,3}(\\.\\d{3})+")) {
				return Optional.of(new BigDecimal(raw.replace(".", "")));
			}
			if (raw.matches("\\d+")) {
				return Optional.of(new BigDecimal(raw));
			}
		}
		catch (NumberFormatException ignored) {
			return Optional.empty();
		}
		return Optional.empty();
	}

	static Optional<OrderStatus> parseStatusToken(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		String token = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		return switch (token) {
			case "paid", "đã thanh toán" -> Optional.of(OrderStatus.PAID);
			case "processing", "đang xử lý" -> Optional.of(OrderStatus.PROCESSING);
			case "completed", "hoàn thành" -> Optional.of(OrderStatus.COMPLETED);
			case "shipped" -> Optional.of(OrderStatus.SHIPPED);
			case "cancelled", "canceled" -> Optional.of(OrderStatus.CANCELLED);
			case "failed" -> Optional.of(OrderStatus.FAILED);
			case "pending", "pending payment" -> Optional.of(OrderStatus.PENDING_PAYMENT);
			default -> Optional.empty();
		};
	}

	private static String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
