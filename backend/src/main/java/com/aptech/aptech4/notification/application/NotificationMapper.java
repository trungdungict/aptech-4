package com.aptech.aptech4.notification.application;

import java.util.UUID;

import com.aptech.aptech4.notification.api.NotificationResponse;
import com.aptech.aptech4.notification.domain.AppNotification;

final class NotificationMapper {

	private NotificationMapper() {
	}

	static NotificationResponse toResponse(AppNotification n) {
		UUID orderId = n.getOrder() == null ? null : n.getOrder().getId();
		return new NotificationResponse(
				n.getId(),
				n.getType().name(),
				n.getTitle(),
				n.getMessage(),
				orderId,
				n.getCreatedAt(),
				n.getReadAt(),
				n.isUnread()
		);
	}
}
