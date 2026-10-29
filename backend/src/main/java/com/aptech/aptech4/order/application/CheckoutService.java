package com.aptech.aptech4.order.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.aptech.aptech4.cart.domain.Cart;
import com.aptech.aptech4.cart.domain.CartItem;
import com.aptech.aptech4.cart.persistence.CartRepository;
import com.aptech.aptech4.catalog.api.ReserveStockRequest;
import com.aptech.aptech4.catalog.api.StockReservationResponse;
import com.aptech.aptech4.catalog.application.InventoryReservationService;
import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.order.api.CheckoutRequest;
import com.aptech.aptech4.order.api.CheckoutResponse;
import com.aptech.aptech4.order.domain.CheckoutIdempotency;
import com.aptech.aptech4.order.domain.OrderItem;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.Payment;
import com.aptech.aptech4.order.domain.PaymentStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.notification.application.OrderNotificationService;
import com.aptech.aptech4.order.payment.MockPaymentProperties;
import com.aptech.aptech4.order.payment.PaymentGateway;
import com.aptech.aptech4.order.persistence.CheckoutIdempotencyRepository;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;

@Service
public class CheckoutService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

	private final CartRepository cartRepository;
	private final AddressRepository addressRepository;
	private final UserAccountRepository userAccountRepository;
	private final ShopOrderRepository shopOrderRepository;
	private final CheckoutIdempotencyRepository checkoutIdempotencyRepository;
	private final InventoryReservationService inventoryReservationService;
	private final PaymentGateway paymentGateway;
	private final MockPaymentProperties mockPaymentProperties;
	private final EntityManager entityManager;
	private final OrderNotificationService orderNotificationService;

	public CheckoutService(
			CartRepository cartRepository,
			AddressRepository addressRepository,
			UserAccountRepository userAccountRepository,
			ShopOrderRepository shopOrderRepository,
			CheckoutIdempotencyRepository checkoutIdempotencyRepository,
			InventoryReservationService inventoryReservationService,
			PaymentGateway paymentGateway,
			MockPaymentProperties mockPaymentProperties,
			EntityManager entityManager,
			OrderNotificationService orderNotificationService
	) {
		this.cartRepository = cartRepository;
		this.addressRepository = addressRepository;
		this.userAccountRepository = userAccountRepository;
		this.shopOrderRepository = shopOrderRepository;
		this.checkoutIdempotencyRepository = checkoutIdempotencyRepository;
		this.inventoryReservationService = inventoryReservationService;
		this.paymentGateway = paymentGateway;
		this.mockPaymentProperties = mockPaymentProperties;
		this.entityManager = entityManager;
		this.orderNotificationService = orderNotificationService;
	}

	@Transactional
	public CheckoutResponse checkout(CheckoutRequest request) {
		UUID userId = CurrentUser.requireUserId();
		String key = request.idempotencyKey().trim();
		boolean simulateFailure = Boolean.TRUE.equals(request.simulateFailure())
				&& mockPaymentProperties.isAllowClientForceFailure();

		// Hash request body only (address + simulateFailure) so PAID replays work after cart clear.
		String requestHash = hashRequest(request.addressId(), simulateFailure);

		var existing = checkoutIdempotencyRepository.findByUserIdAndKey(userId, key);
		if (existing.isPresent()) {
			CheckoutIdempotency row = existing.get();
			if (!row.getRequestHash().equals(requestHash)) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Idempotency key reused with different request");
			}
			return toResponse(loadOrderForResponse(row.getOrder().getId()), row.getOrder().getStatus() == OrderStatus.PAID);
		}

		int locked = entityManager.createNativeQuery("""
				INSERT INTO checkout_locks (user_id, idempotency_key)
				VALUES (:userId, :key)
				ON CONFLICT DO NOTHING
				""")
			.setParameter("userId", userId)
			.setParameter("key", key)
			.executeUpdate();
		if (locked == 0) {
			var raced = checkoutIdempotencyRepository.findByUserIdAndKey(userId, key);
			if (raced.isPresent()) {
				CheckoutIdempotency row = raced.get();
				if (!row.getRequestHash().equals(requestHash)) {
					throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Idempotency key reused with different request");
				}
				return toResponse(loadOrderForResponse(row.getOrder().getId()), row.getOrder().getStatus() == OrderStatus.PAID);
			}
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Checkout already in progress");
		}

		cartRepository.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Cart is empty"));
		Cart cart = cartRepository.findByUserIdWithItems(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Cart is empty"));
		if (cart.getItems().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Cart is empty");
		}

		List<CartItem> lines = cart.getItems().stream()
			.sorted(Comparator.comparing(i -> i.getProduct().getId()))
			.toList();

		Address address = addressRepository.findByIdAndUserId(request.addressId(), userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Address not found"));

		for (CartItem line : lines) {
			if (!isPurchasable(line.getProduct())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Cart contains unavailable products");
			}
		}

		UserAccount user = userAccountRepository.getReferenceById(userId);

		List<ReserveStockRequest.ReserveStockLineRequest> reserveLines = new ArrayList<>();
		for (CartItem line : lines) {
			reserveLines.add(new ReserveStockRequest.ReserveStockLineRequest(
					line.getProduct().getId(),
					line.getQuantity()
			));
		}
		String reservationKey = "checkout:" + key;
		StockReservationResponse reservation = inventoryReservationService.reserve(
				new ReserveStockRequest(reservationKey, reserveLines)
		);

		BigDecimal subtotal = ZERO;
		String currency = "VND";
		ShopOrder order = ShopOrder.createPending(user, address);

		for (CartItem line : lines) {
			Product product = line.getProduct();
			BigDecimal unitPrice = product.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
			OrderItem item = OrderItem.snapshot(product, line.getQuantity(), unitPrice);
			order.addItem(item);
			subtotal = subtotal.add(item.getLineTotal());
			currency = product.getCurrency();
		}
		order.setCurrency(currency);
		order.setSubtotalAmount(subtotal);
		order.setTotalAmount(subtotal.add(order.getShippingAmount()).setScale(2, RoundingMode.HALF_UP));

		Payment payment = Payment.mockPending(order, order.getTotalAmount(), currency);
		order.addPayment(payment);
		ShopOrder saved = shopOrderRepository.saveAndFlush(order);

		PaymentGateway.PaymentChargeResult charge = paymentGateway.charge(
				new PaymentGateway.PaymentChargeCommand(
						saved.getId(),
						saved.getTotalAmount(),
						saved.getCurrency(),
						simulateFailure
				)
		);

		boolean cartCleared = false;
		boolean newlyPaid = false;
		if (charge.succeeded()) {
			payment.setStatus(PaymentStatus.SUCCEEDED);
			payment.setProviderReference(charge.providerReference());
			payment.setFailureReason(null);
			payment.touch();
			inventoryReservationService.confirm(reservation.id(), userId);
			saved.setStatus(OrderStatus.PAID);
			saved.touch();
			clearCart(userId);
			cartCleared = true;
			newlyPaid = true;
		}
		else {
			payment.setStatus(PaymentStatus.FAILED);
			payment.setProviderReference(null);
			payment.setFailureReason(charge.failureReason());
			payment.touch();
			inventoryReservationService.release(reservation.id(), userId);
			saved.setStatus(OrderStatus.FAILED);
			saved.touch();
		}
		shopOrderRepository.saveAndFlush(saved);

		try {
			checkoutIdempotencyRepository.saveAndFlush(
					CheckoutIdempotency.of(user, key, requestHash, saved)
			);
		}
		catch (DataIntegrityViolationException ex) {
			CheckoutIdempotency winner = checkoutIdempotencyRepository.findByUserIdAndKey(userId, key)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Checkout conflict"));
			if (!winner.getRequestHash().equals(requestHash)) {
				throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Idempotency key reused with different request");
			}
			return toResponse(loadOrderForResponse(winner.getOrder().getId()), winner.getOrder().getStatus() == OrderStatus.PAID);
		}

		if (newlyPaid) {
			orderNotificationService.notifyOrderPaid(saved);
		}

		return toResponse(loadOrderForResponse(saved.getId()), cartCleared);
	}

	/**
	 * Load items via join-fetch, then initialize payments separately to avoid MultipleBagFetchException.
	 */
	private ShopOrder loadOrderForResponse(UUID orderId) {
		ShopOrder order = shopOrderRepository.findByIdWithItems(orderId)
			.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "Order missing"));
		order.getPayments().size();
		return order;
	}

	private void clearCart(UUID userId) {
		Cart cart = cartRepository.findByUserIdForUpdate(userId)
			.orElse(null);
		if (cart == null) {
			return;
		}
		Cart managed = cartRepository.findByUserIdWithItems(userId).orElse(cart);
		managed.getItems().clear();
		managed.touch();
		cartRepository.saveAndFlush(managed);
	}

	private static boolean isPurchasable(Product product) {
		if (product == null || !product.isActive()) {
			return false;
		}
		Category category = product.getCategory();
		return category != null && category.isActive();
	}

	private static String hashRequest(UUID addressId, boolean simulateFailure) {
		String canonical = addressId + "|" + simulateFailure;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 not available", ex);
		}
	}

	private CheckoutResponse toResponse(ShopOrder order, boolean cartCleared) {
		List<CheckoutResponse.CheckoutOrderItemResponse> items = order.getItems().stream()
			.sorted(Comparator.comparing(OrderItem::getProductSku))
			.map(i -> new CheckoutResponse.CheckoutOrderItemResponse(
					i.getProduct() == null ? null : i.getProduct().getId(),
					i.getProductSku(),
					i.getProductName(),
					i.getUnitPrice(),
					i.getQuantity(),
					i.getLineTotal()
			))
			.toList();

		Payment payment = order.getPayments().stream()
			.findFirst()
			.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "Payment missing"));

		return new CheckoutResponse(
				order.getId(),
				order.getStatus().name(),
				order.getSubtotalAmount(),
				order.getShippingAmount(),
				order.getTotalAmount(),
				order.getCurrency(),
				order.getPlacedAt(),
				items,
				new CheckoutResponse.CheckoutPaymentResponse(
						payment.getId(),
						payment.getProvider(),
						payment.getStatus().name(),
						payment.getAmount(),
						payment.getCurrency(),
						payment.getProviderReference(),
						payment.getFailureReason()
				),
				cartCleared && order.getStatus() == OrderStatus.PAID
		);
	}
}
