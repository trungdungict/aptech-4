package com.aptech.aptech4.notification.ws;

/**
 * STOMP destinations for private notification push (ADR-018).
 */
public final class NotificationDestinations {

	/** Client subscribes here; Spring resolves to a per-user queue. */
	public static final String USER_QUEUE = "/user/queue/notifications";

	/** Destination suffix for {@code convertAndSendToUser}. */
	public static final String QUEUE_SUFFIX = "/queue/notifications";

	private NotificationDestinations() {
	}
}
