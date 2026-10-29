package com.aptech.aptech4.order.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.notification.application.OrderNotificationService;
import com.aptech.aptech4.order.api.OrderResponse;
import com.aptech.aptech4.order.api.OrderStatusUpdateRequest;
import com.aptech.aptech4.order.domain.OrderItem;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.Payment;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;

@Service
public class OrderService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final ShopOrderRepository shopOrderRepository;
	private final OrderNotificationService orderNotificationService;

	public OrderService(
			ShopOrderRepository shopOrderRepository,
			OrderNotificationService orderNotificationService
	) {
		this.shopOrderRepository = shopOrderRepository;
		this.orderNotificationService = orderNotificationService;
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderResponse> listMine(OrderStatus status, int page, int size, String sort) {
		UUID userId = CurrentUser.requireUserId();
		Pageable pageable = pageable(page, size, sort);
		Page<ShopOrder> result = status == null
				? shopOrderRepository.findByUser_Id(userId, pageable)
				: shopOrderRepository.findByUser_IdAndStatus(userId, status, pageable);
		return mapPage(result);
	}

	@Transactional(readOnly = true)
	public OrderResponse getMine(UUID orderId) {
		UUID userId = CurrentUser.requireUserId();
		ShopOrder order = shopOrderRepository.findByIdAndUserIdWithItems(orderId, userId)
			.orElseThrow(this::notFound);
		order.getPayments().size();
		return toResponse(order);
	}

	@Transactional
	public OrderResponse cancelMine(UUID orderId) {
		UUID userId = CurrentUser.requireUserId();
		ShopOrder order = shopOrderRepository.findByIdAndUserIdForUpdate(orderId, userId)
			.orElseThrow(this::notFound);
		OrderStatusTransitions.assertCustomerCancel(order.getStatus());
		order.setStatus(OrderStatus.CANCELLED);
		order.touch();
		shopOrderRepository.saveAndFlush(order);
		orderNotificationService.notifyOrderCancelled(order);
		return loadDetail(order.getId());
	}

	@Transactional(readOnly = true)
	public PageResponse<OrderResponse> listAdmin(OrderStatus status, int page, int size, String sort) {
		Pageable pageable = pageable(page, size, sort);
		Page<ShopOrder> result = status == null
				? shopOrderRepository.findAll(pageable)
				: shopOrderRepository.findByStatus(status, pageable);
		return mapPage(result);
	}

	@Transactional(readOnly = true)
	public OrderResponse getAdmin(UUID orderId) {
		ShopOrder order = shopOrderRepository.findByIdWithItems(orderId).orElseThrow(this::notFound);
		order.getPayments().size();
		order.getUser().getId();
		return toResponse(order);
	}

	@Transactional
	public OrderResponse updateAdminStatus(UUID orderId, OrderStatusUpdateRequest request) {
		ShopOrder order = shopOrderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		OrderStatus target = request.status();
		OrderStatusTransitions.assertAdminTransition(order.getStatus(), target);
		if (order.getStatus() != target) {
			order.setStatus(target);
			order.touch();
			shopOrderRepository.saveAndFlush(order);
			orderNotificationService.notifyOrderStatusChanged(order, target);
		}
		return loadDetail(order.getId());
	}

	private OrderResponse loadDetail(UUID orderId) {
		ShopOrder order = shopOrderRepository.findByIdWithItems(orderId).orElseThrow(this::notFound);
		order.getPayments().size();
		order.getUser().getId();
		return toResponse(order);
	}

	private PageResponse<OrderResponse> mapPage(Page<ShopOrder> page) {
		List<OrderResponse> items = page.getContent().stream()
			.map(order -> {
				order.getPayments().size();
				order.getItems().size();
				order.getUser().getId();
				return toResponse(order);
			})
			.toList();
		return PageResponse.of(items, page.getNumber(), page.getSize(), page.getTotalElements());
	}

	private Pageable pageable(int page, int size, String sort) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "page must be >= 0");
		}
		if (size < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "size must be >= 1");
		}
		if (size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "size must be <= " + MAX_PAGE_SIZE);
		}
		return PageRequest.of(page, size, OrderSortSupport.resolve(sort));
	}

	private OrderResponse toResponse(ShopOrder order) {
		List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
			.sorted(Comparator.comparing(OrderItem::getProductSku).thenComparing(OrderItem::getId))
			.map(i -> new OrderResponse.OrderItemResponse(
					i.getProduct() == null ? null : i.getProduct().getId(),
					i.getProductSku(),
					i.getProductName(),
					i.getUnitPrice(),
					i.getQuantity(),
					i.getLineTotal()
			))
			.toList();

		Payment payment = order.getPayments().stream()
			.max(Comparator.comparing(Payment::getCreatedAt).thenComparing(Payment::getId))
			.orElse(null);

		OrderResponse.OrderPaymentSummaryResponse paymentSummary = null;
		if (payment != null) {
			paymentSummary = new OrderResponse.OrderPaymentSummaryResponse(
					payment.getId(),
					payment.getProvider(),
					payment.getStatus().name(),
					payment.getAmount(),
					payment.getCurrency(),
					payment.getProviderReference(),
					payment.getFailureReason()
			);
		}

		return new OrderResponse(
				order.getId(),
				order.getUser().getId(),
				order.getStatus().name(),
				order.getSubtotalAmount(),
				order.getShippingAmount(),
				order.getTotalAmount(),
				order.getCurrency(),
				order.getPlacedAt(),
				order.getUpdatedAt(),
				new OrderResponse.OrderShippingResponse(
						order.getShippingRecipientName(),
						order.getShippingLine1(),
						order.getShippingLine2(),
						order.getShippingCity(),
						order.getShippingStateRegion(),
						order.getShippingPostalCode(),
						order.getShippingCountryCode(),
						order.getShippingPhone()
				),
				items,
				paymentSummary
		);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Order not found");
	}
}
