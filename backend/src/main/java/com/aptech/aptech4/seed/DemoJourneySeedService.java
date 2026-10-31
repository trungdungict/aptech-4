package com.aptech.aptech4.seed;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.cart.domain.Cart;
import com.aptech.aptech4.cart.domain.CartItem;
import com.aptech.aptech4.cart.persistence.CartRepository;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.EmailNormalizer;
import com.aptech.aptech4.notification.domain.AppNotification;
import com.aptech.aptech4.notification.domain.NotificationType;
import com.aptech.aptech4.notification.persistence.AppNotificationRepository;
import com.aptech.aptech4.order.domain.OrderItem;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.Payment;
import com.aptech.aptech4.order.domain.PaymentStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;

/**
 * Idempotent customer-journey fixtures (addresses, cart, sample orders, notifications).
 * Gated by notification dedupe key {@code seed:journey:v1} on the primary demo customer.
 */
@Service
public class DemoJourneySeedService {

	static final String JOURNEY_DEDUPE = "seed:journey:v1";

	private final DemoSeedProperties properties;
	private final UserAccountRepository userAccountRepository;
	private final AddressRepository addressRepository;
	private final ProductRepository productRepository;
	private final CartRepository cartRepository;
	private final ShopOrderRepository shopOrderRepository;
	private final AppNotificationRepository notificationRepository;
	private final PasswordEncoder passwordEncoder;

	public DemoJourneySeedService(
			DemoSeedProperties properties,
			UserAccountRepository userAccountRepository,
			AddressRepository addressRepository,
			ProductRepository productRepository,
			CartRepository cartRepository,
			ShopOrderRepository shopOrderRepository,
			AppNotificationRepository notificationRepository,
			PasswordEncoder passwordEncoder
	) {
		this.properties = properties;
		this.userAccountRepository = userAccountRepository;
		this.addressRepository = addressRepository;
		this.productRepository = productRepository;
		this.cartRepository = cartRepository;
		this.shopOrderRepository = shopOrderRepository;
		this.notificationRepository = notificationRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public record JourneyCounts(int usersCreated, int addressesUpserted, int ordersSeeded, int notificationsSeeded) {
	}

	public JourneyCounts seed(Role customerRole) {
		UserAccount primary = userAccountRepository
				.findByEmailWithRoles(EmailNormalizer.normalize(properties.getCustomerEmail()))
				.orElseThrow(() -> new IllegalStateException("Primary demo customer missing before journey seed"));

		if (notificationRepository.existsByRecipient_IdAndDedupeKey(primary.getId(), JOURNEY_DEDUPE)) {
			return new JourneyCounts(0, 0, 0, 0);
		}

		int usersCreated = 0;
		String secondaryEmail = EmailNormalizer.normalize(DemoSeedMarkers.SECONDARY_CUSTOMER_EMAIL);
		UserAccount secondaryAccount = userAccountRepository.findByEmailWithRoles(secondaryEmail).orElse(null);
		if (secondaryAccount == null) {
			secondaryAccount = UserAccount.createNew(
					secondaryEmail,
					passwordEncoder.encode(properties.getCustomerPassword()),
					"Nguyễn Thị Lan"
			);
			secondaryAccount.getRoles().add(customerRole);
			userAccountRepository.save(secondaryAccount);
			usersCreated = 1;
		}
		final UserAccount secondary = secondaryAccount;

		int addresses = 0;
		Address primaryHome = ensureAddress(
				primary,
				"Nguyễn Văn Minh",
				"12 Nguyễn Huệ",
				"Quận 1",
				"Hồ Chí Minh",
				"700000",
				"0901234567",
				true
		);
		addresses++;
		ensureAddress(
				secondary,
				"Nguyễn Thị Lan",
				"45 Lê Lợi",
				"Hải Châu",
				"Đà Nẵng",
				"550000",
				"0912345678",
				true
		);
		addresses++;

		Product banana = productRepository.findBySkuIgnoreCase("FOOD-FRUIT-BANANA-001").orElse(null);
		Product milk = productRepository.findBySkuIgnoreCase("FOOD-DAIRY-MILK-001").orElse(null);
		Product apple = productRepository.findBySkuIgnoreCase("FOOD-FRUIT-APPLE-001").orElse(null);
		if (banana == null || milk == null || apple == null) {
			throw new IllegalStateException("FoodMart products missing before journey seed");
		}

		Cart cart = cartRepository.findByUserIdWithItems(secondary.getId())
				.orElseGet(() -> cartRepository.save(Cart.createFor(secondary)));
		if (cart.getItems().isEmpty()) {
			cart.addItem(CartItem.createNew(cart, banana, 2));
			cart.addItem(CartItem.createNew(cart, milk, 1));
			cartRepository.save(cart);
		}

		int orders = 0;
		orders += seedOrder(primary, primaryHome, List.of(line(banana, 3), line(milk, 2)), OrderStatus.PENDING_PAYMENT, PaymentStatus.PENDING);
		orders += seedOrder(primary, primaryHome, List.of(line(milk, 1)), OrderStatus.PAID, PaymentStatus.SUCCEEDED);
		orders += seedOrder(primary, primaryHome, List.of(line(banana, 1)), OrderStatus.PROCESSING, PaymentStatus.SUCCEEDED);
		orders += seedOrder(primary, primaryHome, List.of(line(banana, 2), line(milk, 1)), OrderStatus.SHIPPED, PaymentStatus.SUCCEEDED);
		orders += seedOrder(primary, primaryHome, List.of(line(milk, 2)), OrderStatus.COMPLETED, PaymentStatus.SUCCEEDED);
		orders += seedOrder(primary, primaryHome, List.of(line(banana, 1)), OrderStatus.CANCELLED, PaymentStatus.CANCELLED);
		orders += seedOrder(
				secondary,
				addressRepository.findByUser_IdOrderByDefaultAddressDescCreatedAtDesc(secondary.getId()).getFirst(),
				List.of(line(apple, 1)),
				OrderStatus.FAILED,
				PaymentStatus.FAILED
		);

		int notifications = 0;
		notifications += saveNotification(
				primary,
				NotificationType.ORDER_PAID,
				"Đơn hàng đã thanh toán",
				"Demo: đơn hàng FoodMart của bạn đã được thanh toán thành công.",
				"seed:notif:paid:v1"
		);
		notifications += saveNotification(
				primary,
				NotificationType.ORDER_STATUS_CHANGED,
				"Đơn hàng đang giao",
				"Demo: đơn hàng mẫu đã chuyển sang trạng thái đang giao.",
				"seed:notif:shipped:v1"
		);
		notifications += saveNotification(
				primary,
				NotificationType.ORDER_CANCELLED,
				"Đơn hàng đã hủy",
				"Demo: một đơn hàng mẫu đã được hủy.",
				"seed:notif:cancelled:v1"
		);
		notificationRepository.save(AppNotification.create(
				primary,
				NotificationType.ORDER_STATUS_CHANGED,
				"Demo journey sẵn sàng",
				"Seed journey v1 hoàn tất — catalog FoodMart + đơn hàng mẫu.",
				null,
				JOURNEY_DEDUPE
		));
		notifications++;

		return new JourneyCounts(usersCreated, addresses, orders, notifications);
	}

	private record Line(Product product, int qty) {
	}

	private static Line line(Product product, int qty) {
		return new Line(product, qty);
	}

	private Address ensureAddress(
			UserAccount user,
			String recipient,
			String line1,
			String district,
			String city,
			String postal,
			String phone,
			boolean defaultAddress
	) {
		List<Address> existing = addressRepository.findByUser_IdOrderByDefaultAddressDescCreatedAtDesc(user.getId());
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		return addressRepository.save(Address.createNew(
				user,
				recipient,
				line1,
				district,
				city,
				null,
				postal,
				"VN",
				phone,
				defaultAddress
		));
	}

	private int seedOrder(
			UserAccount user,
			Address address,
			List<Line> lines,
			OrderStatus orderStatus,
			PaymentStatus paymentStatus
	) {
		ShopOrder order = ShopOrder.createPending(user, address);
		BigDecimal subtotal = BigDecimal.ZERO;
		for (Line line : lines) {
			OrderItem item = OrderItem.snapshot(line.product(), line.qty(), line.product().getUnitPrice());
			order.addItem(item);
			subtotal = subtotal.add(item.getLineTotal());
		}
		order.setCurrency("VND");
		order.setSubtotalAmount(subtotal);
		order.setShippingAmount(BigDecimal.ZERO);
		order.setTotalAmount(subtotal);
		order.setStatus(orderStatus);

		Payment payment = Payment.mockPending(order, subtotal, "VND");
		payment.setStatus(paymentStatus);
		if (paymentStatus == PaymentStatus.SUCCEEDED) {
			payment.setProviderReference("SEED-" + orderStatus.name() + "-" + user.getId().toString().substring(0, 8));
		}
		order.addPayment(payment);

		shopOrderRepository.save(order);
		return 1;
	}

	private int saveNotification(
			UserAccount recipient,
			NotificationType type,
			String title,
			String message,
			String dedupeKey
	) {
		if (notificationRepository.existsByRecipient_IdAndDedupeKey(recipient.getId(), dedupeKey)) {
			return 0;
		}
		notificationRepository.save(AppNotification.create(recipient, type, title, message, null, dedupeKey));
		return 1;
	}
}
