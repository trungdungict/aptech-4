package com.aptech.aptech4.chat.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.aptech.aptech4.catalog.api.ProductResponse;
import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.chat.application.ChatToolJson;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.order.api.OrderResponse;
import com.aptech.aptech4.order.application.OrderService;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only customer tools. Order tools always use {@link CurrentUser#requireUserId()} —
 * never a caller-supplied customer id.
 */
@Component
public class CustomerChatTools {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

	private final ProductService productService;
	private final OrderService orderService;
	private final AiProperties aiProperties;

	public CustomerChatTools(
			ProductService productService,
			OrderService orderService,
			AiProperties aiProperties
	) {
		this.productService = productService;
		this.orderService = orderService;
		this.aiProperties = aiProperties;
	}

	@Tool(description = "Search the public product catalog by keyword, optional category id, price range and in-stock filter. Returns active products only.")
	public String searchProducts(
			@ToolParam(description = "Free-text search query; may be empty") String query,
			@ToolParam(description = "Optional category UUID", required = false) String categoryId,
			@ToolParam(description = "Minimum unit price", required = false) Double minPrice,
			@ToolParam(description = "Maximum unit price", required = false) Double maxPrice,
			@ToolParam(description = "When true, only products with available stock > 0", required = false) Boolean inStock,
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		int size = clampLimit(limit);
		UUID cat = parseUuidOrNull(categoryId);
		PageResponse<ProductResponse> page = productService.listPublic(
				blankToNull(query),
				cat,
				minPrice == null ? null : BigDecimal.valueOf(minPrice),
				maxPrice == null ? null : BigDecimal.valueOf(maxPrice),
				inStock,
				0,
				size,
				"name,asc"
		);
		return ChatToolJson.write(OBJECT_MAPPER, Map.of(
				"totalElements", page.totalElements(),
				"items", page.items().stream().map(this::productSummary).toList(),
				"pathHint", "/products"
		));
	}

	@Tool(description = "Get public details for one product by UUID.")
	public String getProductDetails(@ToolParam(description = "Product UUID") String productId) {
		ProductResponse product = productService.getPublic(UUID.fromString(productId.trim()));
		Map<String, Object> body = productSummary(product);
		body.put("description", product.description());
		body.put("path", "/products/" + product.id());
		return ChatToolJson.write(OBJECT_MAPPER, body);
	}

	@Tool(description = "List the authenticated customer's own orders (newest first). Optional status filter.")
	public String getMyOrders(
			@ToolParam(description = "Optional order status: PENDING_PAYMENT,PAID,PROCESSING,SHIPPED,COMPLETED,CANCELLED,FAILED", required = false) String status,
			@ToolParam(description = "Max results 1-20", required = false) Integer limit
	) {
		CurrentUser.requireUserId();
		OrderStatus parsed = parseStatusOrNull(status);
		int size = clampLimit(limit);
		PageResponse<OrderResponse> page = orderService.listMine(parsed, 0, size, "placedAt,desc");
		return ChatToolJson.write(OBJECT_MAPPER, Map.of(
				"totalElements", page.totalElements(),
				"items", page.items().stream().map(this::orderSummary).toList(),
				"pathHint", "/orders"
		));
	}

	@Tool(description = "Get one of the authenticated customer's orders by UUID, including line items.")
	public String getMyOrderDetails(@ToolParam(description = "Order UUID") String orderId) {
		CurrentUser.requireUserId();
		OrderResponse order = orderService.getMine(UUID.fromString(orderId.trim()));
		return ChatToolJson.write(OBJECT_MAPPER, orderDetail(order));
	}

	@Tool(description = "Summarize the authenticated customer's most recent order, or report that none exist.")
	public String getMyLatestOrderSummary() {
		CurrentUser.requireUserId();
		PageResponse<OrderResponse> page = orderService.listMine(null, 0, 1, "placedAt,desc");
		if (page.items().isEmpty()) {
			return ChatToolJson.write(OBJECT_MAPPER, Map.of(
					"found", false,
					"message", "No orders found for this customer."
			));
		}
		OrderResponse order = orderService.getMine(page.items().getFirst().id());
		Map<String, Object> body = orderDetail(order);
		body.put("found", true);
		return ChatToolJson.write(OBJECT_MAPPER, body);
	}

	@Tool(description = "Return short checkout/cart help based on application workflows (no live cart mutation).")
	public String getCheckoutHelp() {
		return ChatToolJson.write(OBJECT_MAPPER, Map.of(
				"steps", java.util.List.of(
						"Add products from /products to the cart.",
						"Open /cart to review quantities.",
						"Ensure an address exists, then proceed to /checkout.",
						"Successful mock payment places the order as PAID.",
						"View history at /orders."
				),
				"commonIssues", java.util.List.of(
						"Insufficient stock / reservation expired — reduce quantity or retry.",
						"Missing address — create one before checkout.",
						"Not signed in — sign in required for cart and checkout."
				)
		));
	}

	private Map<String, Object> productSummary(ProductResponse product) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", product.id());
		map.put("sku", product.sku());
		map.put("name", product.name());
		map.put("unitPrice", product.unitPrice());
		map.put("currency", product.currency());
		map.put("categoryId", product.categoryId());
		map.put("categoryName", product.categoryName());
		map.put("inStock", product.inStock());
		map.put("active", product.active());
		map.put("path", "/products/" + product.id());
		return map;
	}

	private Map<String, Object> orderSummary(OrderResponse order) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", order.id());
		map.put("status", order.status());
		map.put("totalAmount", order.totalAmount());
		map.put("currency", order.currency());
		map.put("placedAt", order.placedAt());
		map.put("itemCount", order.items() == null ? 0 : order.items().size());
		map.put("path", "/orders/" + order.id());
		return map;
	}

	private Map<String, Object> orderDetail(OrderResponse order) {
		Map<String, Object> map = orderSummary(order);
		map.put("subtotalAmount", order.subtotalAmount());
		map.put("shippingAmount", order.shippingAmount());
		map.put("items", order.items());
		map.put("payment", order.payment());
		return map;
	}

	private int clampLimit(Integer limit) {
		int max = aiProperties.getMaxToolResults();
		if (limit == null) {
			return Math.min(10, max);
		}
		return Math.max(1, Math.min(limit, max));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static UUID parseUuidOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return UUID.fromString(raw.trim());
	}

	private static OrderStatus parseStatusOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return OrderStatus.valueOf(raw.trim().toUpperCase());
	}
}
