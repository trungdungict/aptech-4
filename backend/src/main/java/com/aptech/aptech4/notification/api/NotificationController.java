package com.aptech.aptech4.notification.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.notification.application.NotificationQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Recipient-scoped notification feed. Each authenticated user (CUSTOMER or ADMIN) only sees
 * rows where {@code recipient_user_id} matches their token subject — there is no cross-user
 * aggregate feed; ADMIN operational events are delivered as personal notifications to users
 * holding the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Notifications", description = "In-app order notifications for the authenticated recipient")
public class NotificationController {

	private final NotificationQueryService notificationQueryService;

	public NotificationController(NotificationQueryService notificationQueryService) {
		this.notificationQueryService = notificationQueryService;
	}

	@GetMapping
	@Operation(summary = "List my notifications",
			description = "Newest first. Zero-based page. Default size=20, max=100.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of notifications"),
			@ApiResponse(responseCode = "401", description = "Unauthorized")
	})
	public ResponseEntity<PageResponse<NotificationResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + NotificationQueryService.DEFAULT_PAGE_SIZE) int size
	) {
		return ResponseEntity.ok(notificationQueryService.listMine(page, size));
	}

	@GetMapping("/unread-count")
	@Operation(summary = "Unread notification count")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Count"),
			@ApiResponse(responseCode = "401", description = "Unauthorized")
	})
	public ResponseEntity<UnreadCountResponse> unreadCount() {
		return ResponseEntity.ok(notificationQueryService.unreadCount());
	}

	@PatchMapping("/{id}/read")
	@Operation(summary = "Mark one notification as read")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated",
					content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
			@ApiResponse(responseCode = "404", description = "Missing or not owned",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id) {
		return ResponseEntity.ok(notificationQueryService.markRead(id));
	}

	@PatchMapping("/read-all")
	@Operation(summary = "Mark all my notifications as read")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Unread count after update"),
			@ApiResponse(responseCode = "401", description = "Unauthorized")
	})
	public ResponseEntity<UnreadCountResponse> markAllRead() {
		return ResponseEntity.ok(notificationQueryService.markAllRead());
	}
}
