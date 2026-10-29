package com.aptech.aptech4.notification.ws;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.notification.api.NotificationResponse;

/**
 * Pushes already-persisted notifications to the recipient's private STOMP queue.
 * Call only after the creating transaction has committed.
 */
@Service
public class NotificationPushService {

	private static final Logger log = LoggerFactory.getLogger(NotificationPushService.class);

	private final SimpMessagingTemplate messagingTemplate;

	public NotificationPushService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	public void pushToUser(UUID recipientUserId, NotificationResponse payload) {
		if (recipientUserId == null || payload == null) {
			return;
		}
		long t2 = System.nanoTime();
		try {
			messagingTemplate.convertAndSendToUser(
					recipientUserId.toString(),
					NotificationDestinations.QUEUE_SUFFIX,
					payload
			);
			if (log.isDebugEnabled()) {
				long handedOffMs = (System.nanoTime() - t2) / 1_000_000L;
				log.debug(
						"STOMP push handed off notificationId={} recipientHash={} durationMs={}",
						payload.id(),
						Integer.toHexString(recipientUserId.hashCode()),
						handedOffMs
				);
			}
		}
		catch (RuntimeException ex) {
			// Persistence already succeeded; offline clients recover via REST on reconnect.
			log.debug("STOMP push skipped for recipientHash={}: {}",
					Integer.toHexString(recipientUserId.hashCode()), ex.toString());
		}
	}
}
