package com.aptech.aptech4.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unread notification count for the authenticated user")
public record UnreadCountResponse(
		@Schema(description = "Number of unread notifications", example = "3") long unreadCount
) {
}
