package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.catalog.api.ProductResponse;
import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.chat.api.ChatLanguage;
import com.aptech.aptech4.chat.api.ChatTiming;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.order.api.OrderResponse;
import com.aptech.aptech4.order.application.OrderService;
import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * High-confidence Customer answers from JWT-scoped services — no free-form LLM for these intents.
 * Customer identity always comes from {@link CurrentUser#requireUserId()}, never from the model.
 */
@Service
public class CustomerDeterministicAssistantService {

	private static final Logger log = LoggerFactory.getLogger(CustomerDeterministicAssistantService.class);
	private static final int LIST_LIMIT = 10;

	private final OrderService orderService;
	private final ProductService productService;

	public CustomerDeterministicAssistantService(OrderService orderService, ProductService productService) {
		this.orderService = orderService;
		this.productService = productService;
	}

	public record DeterministicAnswer(String reply, ChatTiming timing) {
	}

	public DeterministicAnswer answer(CustomerIntentMatch match, ChatLanguage language) {
		if (match == null || match.kind() == CustomerIntentKind.NONE) {
			return null;
		}
		long started = System.nanoTime();
		try {
			return switch (match.kind()) {
				case LATEST_ORDER -> latestOrder(language, started);
				case ORDER_COUNT -> orderCount(null, language, started);
				case ORDER_COUNT_BY_STATUS -> orderCount(match.orderStatus(), language, started);
				case ORDER_LIST_BY_STATUS -> orderList(match.orderStatus(), language, started);
				case PRODUCT_UNDER_PRICE -> productsUnder(match.maxPrice(), language, started);
				case CHECKOUT_HELP -> checkoutHelp(language, started);
				case ADMIN_ANALYTICS_FORBIDDEN -> adminAnalyticsForbidden(language, started);
				case NONE -> null;
			};
		}
		catch (ApiException ex) {
			long ms = elapsedMs(started);
			if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
				log.warn("deterministic_customer status=FAILURE category=UNAUTHORIZED durationMs={}", ms);
				return unauthenticatedAnswer(language, ms);
			}
			if (ex.getStatus() == HttpStatus.FORBIDDEN) {
				log.warn("deterministic_customer status=FAILURE category=FORBIDDEN durationMs={}", ms);
				return forbiddenAnswer(language, ms);
			}
			log.warn(
					"deterministic_customer status=FAILURE category=API durationMs={} httpStatus={}",
					ms,
					ex.getStatus().value()
			);
			boolean vi = language == ChatLanguage.VI;
			return new DeterministicAnswer(
					vi ? "Không lấy được dữ liệu đơn hàng. Không suy diễn mã đơn hoặc số lượng."
							: "Could not load order data. No order IDs or counts were assumed.",
					new ChatTiming(ms, 0L, ms, "deterministic_customer_failure")
			);
		}
		catch (RuntimeException ex) {
			long ms = elapsedMs(started);
			log.error("deterministic_customer status=FAILURE category=INTERNAL durationMs={}", ms, ex);
			boolean vi = language == ChatLanguage.VI;
			return new DeterministicAnswer(
					vi ? "Truy vấn thất bại. Không suy diễn mã đơn hoặc số lượng."
							: "That query failed. No order IDs or counts were assumed.",
					new ChatTiming(ms, 0L, ms, "deterministic_customer_failure")
			);
		}
	}

	private DeterministicAnswer adminAnalyticsForbidden(ChatLanguage language, long started) {
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		log.info("deterministic_customer kind=ADMIN_ANALYTICS_FORBIDDEN durationMs={}", ms);
		String reply = vi
				? "Phân tích đơn hàng toàn cửa hàng chỉ dành cho quản trị viên."
				: "Store-wide order analytics are available to administrators only.";
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_customer_forbidden"));
	}

	private DeterministicAnswer orderCount(OrderStatus status, ChatLanguage language, long started) {
		UUID userId = CurrentUser.requireUserId();
		PageResponse<OrderResponse> page = orderService.listMine(status, 0, 1, "placedAt,desc");
		long count = page.totalElements();
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		log.info(
				"deterministic_customer tool=getMyOrderCount userId={} status={} count={} durationMs={}",
				userId,
				status == null ? "ALL" : status.name(),
				count,
				ms
		);
		String reply;
		if (status == null) {
			reply = vi
					? "Bạn đã đặt %d đơn hàng.".formatted(count)
					: (count == 1L ? "You have placed 1 order." : "You have placed %d orders.".formatted(count));
		}
		else if (vi) {
			reply = "Bạn có %d đơn hàng ở trạng thái %s.".formatted(count, status.name());
		}
		else {
			reply = count == 1L
					? "You have 1 %s order.".formatted(status.name())
					: "You have %d %s orders.".formatted(count, status.name());
		}
		String path = status == null ? "deterministic_order_count" : "deterministic_order_count_by_status";
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, path));
	}

	private DeterministicAnswer orderList(OrderStatus status, ChatLanguage language, long started) {
		UUID userId = CurrentUser.requireUserId();
		if (status == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "order status is required");
		}
		PageResponse<OrderResponse> page = orderService.listMine(status, 0, LIST_LIMIT, "placedAt,desc");
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		log.info(
				"deterministic_customer tool=getMyOrders userId={} status={} total={} returned={} durationMs={}",
				userId,
				status.name(),
				page.totalElements(),
				page.items().size(),
				ms
		);
		if (page.items().isEmpty()) {
			String reply = vi
					? "Bạn không có đơn hàng nào ở trạng thái %s.".formatted(status.name())
					: "You have no %s orders.".formatted(status.name());
			return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_orders_by_status"));
		}
		StringBuilder sb = new StringBuilder();
		if (vi) {
			sb.append("Đơn hàng %s của bạn (%d):\n".formatted(status.name(), page.totalElements()));
		}
		else {
			sb.append("Your %s orders (%d):\n".formatted(status.name(), page.totalElements()));
		}
		int i = 1;
		for (OrderResponse order : page.items()) {
			sb.append(i++)
					.append(". ")
					.append(order.id())
					.append(" — ")
					.append(order.totalAmount())
					.append(' ')
					.append(order.currency())
					.append(" (/orders/")
					.append(order.id())
					.append(")\n");
		}
		if (page.totalElements() > page.items().size()) {
			sb.append(vi ? "…xem thêm tại /orders" : "…and more at /orders");
		}
		return new DeterministicAnswer(sb.toString().trim(), new ChatTiming(ms, 0L, ms, "deterministic_orders_by_status"));
	}

	private DeterministicAnswer latestOrder(ChatLanguage language, long started) {
		UUID userId = CurrentUser.requireUserId();
		PageResponse<OrderResponse> page = orderService.listMine(null, 0, 1, "placedAt,desc");
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		if (page.items().isEmpty()) {
			log.info("deterministic_customer tool=getMyLatestOrderSummary found=false userId={} durationMs={}", userId, ms);
			String reply = vi
					? "Bạn chưa có đơn hàng nào."
					: "You do not have any orders yet.";
			return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_latest_order"));
		}
		OrderResponse order = orderService.getMine(page.items().getFirst().id());
		log.info(
				"deterministic_customer tool=getMyLatestOrderSummary found=true orderId={} status={} durationMs={}",
				order.id(),
				order.status(),
				ms
		);
		String reply = vi
				? "Đơn hàng gần nhất của bạn: trạng thái %s, tổng %s %s. Chi tiết: /orders/%s"
						.formatted(order.status(), order.totalAmount(), order.currency(), order.id())
				: "Your latest order status is %s with a total of %s %s. Details: /orders/%s"
						.formatted(order.status(), order.totalAmount(), order.currency(), order.id());
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_latest_order"));
	}

	private DeterministicAnswer productsUnder(BigDecimal maxPrice, ChatLanguage language, long started) {
		BigDecimal cap = maxPrice == null ? new BigDecimal("2000000") : maxPrice;
		// inStock=true → available = on_hand - reserved > 0 (ADR-006 / ProductSpecifications)
		PageResponse<ProductResponse> page = productService.listPublic(
				null,
				null,
				null,
				cap,
				Boolean.TRUE,
				0,
				10,
				"name,asc"
		);
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		log.info(
				"deterministic_customer tool=searchProducts maxPrice={} inStock=true total={} durationMs={}",
				cap,
				page.totalElements(),
				ms
		);
		if (page.items().isEmpty()) {
			String reply = vi
					? "Không có sản phẩm còn hàng dưới %s VND.".formatted(cap)
					: "No in-stock products found under %s VND.".formatted(cap);
			return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_products_under"));
		}
		StringBuilder sb = new StringBuilder();
		if (vi) {
			sb.append("Các sản phẩm còn hàng dưới ").append(cap).append(" VND:\n");
		}
		else {
			sb.append("Here are the available products under ").append(cap).append(" VND:\n");
		}
		int i = 1;
		for (ProductResponse product : page.items()) {
			sb.append(i++)
					.append(". ")
					.append(product.name())
					.append(" — ")
					.append(product.unitPrice())
					.append(' ')
					.append(product.currency())
					.append(" (/products/")
					.append(product.id())
					.append(")\n");
		}
		if (page.totalElements() > page.items().size()) {
			sb.append(vi
					? "…và thêm kết quả tại /products"
					: "…and more at /products");
		}
		return new DeterministicAnswer(sb.toString().trim(), new ChatTiming(ms, 0L, ms, "deterministic_products_under"));
	}

	private DeterministicAnswer checkoutHelp(ChatLanguage language, long started) {
		long ms = elapsedMs(started);
		boolean vi = language == ChatLanguage.VI;
		log.info("deterministic_customer tool=getCheckoutHelp durationMs={}", ms);
		String reply = vi
				? """
				Cách hoàn tất checkout:
				1. Thêm sản phẩm từ /products vào giỏ.
				2. Mở /cart để kiểm tra số lượng.
				3. Tạo hoặc chọn địa chỉ giao hàng (bắt buộc), rồi vào /checkout.
				4. Thanh toán mock thành công → đơn ở trạng thái PAID.
				5. Xem lịch sử tại /orders.

				Lưu ý thường gặp: hết hàng / giữ chỗ hết hạn; thiếu địa chỉ; chưa đăng nhập.
				""".trim()
				: """
				How to complete checkout:
				1. Add products from /products to the cart.
				2. Open /cart to review quantities.
				3. Create or select a shipping address (required), then go to /checkout.
				4. Successful mock payment places the order as PAID.
				5. View history at /orders.

				Common issues: insufficient stock / expired reservation; missing address; not signed in.
				""".trim();
		return new DeterministicAnswer(reply, new ChatTiming(ms, 0L, ms, "deterministic_checkout_help"));
	}

	private static DeterministicAnswer unauthenticatedAnswer(ChatLanguage language, long ms) {
		boolean vi = language == ChatLanguage.VI;
		return new DeterministicAnswer(
				vi ? "Bạn cần đăng nhập để xem đơn hàng của mình."
						: "Please sign in to view your orders. Authentication is required.",
				new ChatTiming(ms, 0L, ms, "deterministic_customer_auth")
		);
	}

	private static DeterministicAnswer forbiddenAnswer(ChatLanguage language, long ms) {
		boolean vi = language == ChatLanguage.VI;
		return new DeterministicAnswer(
				vi ? "Bạn không có quyền thực hiện thao tác này."
						: "You do not have permission to perform this action.",
				new ChatTiming(ms, 0L, ms, "deterministic_customer_forbidden")
		);
	}

	private static long elapsedMs(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}
}
