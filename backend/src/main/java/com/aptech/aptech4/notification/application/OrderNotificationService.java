package com.aptech.aptech4.notification.application;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.notification.api.NotificationResponse;
import com.aptech.aptech4.notification.domain.AppNotification;
import com.aptech.aptech4.notification.domain.NotificationType;
import com.aptech.aptech4.notification.persistence.AppNotificationRepository;
import com.aptech.aptech4.notification.ws.NotificationPushService;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;

/**
 * Creates recipient-scoped order notifications inside the caller's transaction.
 * Dedupe keys prevent duplicates on idempotent checkout replays and unchanged status.
 * STOMP push runs only after commit (ADR-018).
 */
@Service
public class OrderNotificationService {

	private static final Logger log = LoggerFactory.getLogger(OrderNotificationService.class);

	private final AppNotificationRepository notificationRepository;
	private final UserAccountRepository userAccountRepository;
	private final NotificationPushService notificationPushService;

	public OrderNotificationService(
			AppNotificationRepository notificationRepository,
			UserAccountRepository userAccountRepository,
			NotificationPushService notificationPushService
	) {
		this.notificationRepository = notificationRepository;
		this.userAccountRepository = userAccountRepository;
		this.notificationPushService = notificationPushService;
	}

	/**
	 * After successful checkout (order PAID): notify owning customer and all enabled ADMIN users.
	 */
	@Transactional
	public void notifyOrderPaid(ShopOrder order) {
		UserAccount customer = order.getUser();
		UUID orderId = order.getId();
		String shortId = shortId(orderId);

		persistIfAbsent(
				customer,
				NotificationType.ORDER_PAID,
				"Order placed",
				"Your order #" + shortId + " was placed successfully and is paid.",
				order,
				"ORDER_PAID:" + orderId + ":customer"
		);

		String adminTitle = "New paid order";
		String adminMessage = "Order #" + shortId + " was paid and needs fulfillment attention.";
		for (UserAccount admin : enabledAdmins()) {
			persistIfAbsent(
					admin,
					NotificationType.ORDER_PAID,
					adminTitle,
					adminMessage,
					order,
					"ORDER_PAID:" + orderId + ":admin:" + admin.getId()
			);
		}
	}

	/**
	 * After a successful cancel (customer or admin): notify customer and enabled ADMIN users.
	 */
	@Transactional
	public void notifyOrderCancelled(ShopOrder order) {
		UserAccount customer = order.getUser();
		UUID orderId = order.getId();
		String shortId = shortId(orderId);

		persistIfAbsent(
				customer,
				NotificationType.ORDER_CANCELLED,
				"Order cancelled",
				"Your order #" + shortId + " has been cancelled.",
				order,
				"ORDER_CANCELLED:" + orderId + ":customer"
		);

		String adminTitle = "Order cancelled";
		String adminMessage = "Order #" + shortId + " was cancelled.";
		for (UserAccount admin : enabledAdmins()) {
			persistIfAbsent(
					admin,
					NotificationType.ORDER_CANCELLED,
					adminTitle,
					adminMessage,
					order,
					"ORDER_CANCELLED:" + orderId + ":admin:" + admin.getId()
			);
		}
	}

	/**
	 * After a successful admin status transition (non-cancel): notify owning customer only.
	 */
	@Transactional
	public void notifyOrderStatusChanged(ShopOrder order, OrderStatus newStatus) {
		if (newStatus == OrderStatus.CANCELLED) {
			notifyOrderCancelled(order);
			return;
		}
		UserAccount customer = order.getUser();
		UUID orderId = order.getId();
		String shortId = shortId(orderId);
		String statusLabel = humanStatus(newStatus);

		persistIfAbsent(
				customer,
				NotificationType.ORDER_STATUS_CHANGED,
				"Order update",
				"Your order #" + shortId + " is now " + statusLabel + ".",
				order,
				"ORDER_STATUS:" + orderId + ":" + newStatus.name()
		);
	}

	private List<UserAccount> enabledAdmins() {
		return userAccountRepository.findEnabledByRoleCode(RoleCode.ADMIN);
	}

	private void persistIfAbsent(
			UserAccount recipient,
			NotificationType type,
			String title,
			String message,
			ShopOrder order,
			String dedupeKey
	) {
		UUID recipientId = recipient.getId();
		if (notificationRepository.existsByRecipient_IdAndDedupeKey(recipientId, dedupeKey)) {
			return;
		}
		try {
			AppNotification saved = notificationRepository.saveAndFlush(AppNotification.create(
					recipient, type, title, message, order, dedupeKey
			));
			schedulePushAfterCommit(recipientId, NotificationMapper.toResponse(saved));
		}
		catch (DataIntegrityViolationException ex) {
			log.debug("Duplicate notification skipped for recipient={} dedupe={}", recipientId, dedupeKey);
		}
	}

	private void schedulePushAfterCommit(UUID recipientUserId, NotificationResponse payload) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			notificationPushService.pushToUser(recipientUserId, payload);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				notificationPushService.pushToUser(recipientUserId, payload);
			}
		});
	}

	private static String shortId(UUID orderId) {
		return orderId.toString().substring(0, 8);
	}

	private static String humanStatus(OrderStatus status) {
		return switch (status) {
			case PROCESSING -> "processing";
			case SHIPPED -> "shipped";
			case COMPLETED -> "completed";
			case PAID -> "paid";
			case PENDING_PAYMENT -> "awaiting payment";
			case CANCELLED -> "cancelled";
			case FAILED -> "failed";
		};
	}
}
