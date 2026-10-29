package com.aptech.aptech4.notification.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "In-app notification for the authenticated recipient")
public record NotificationResponse(
		@Schema(description = "Notification id") UUID id,
		@Schema(description = "Event type key", example = "ORDER_PAID") String type,
		@Schema(description = "Short title") String title,
		@Schema(description = "Body message") String message,
		@Schema(description = "Related order id when applicable") UUID orderId,
		@Schema(description = "Created timestamp (UTC)") Instant createdAt,
		@Schema(description = "Read timestamp when marked read; null if unread") Instant readAt,
		@Schema(description = "Whether the notification is unread") boolean unread
) {
}
