package com.aptech.aptech4.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.notification.api.NotificationResponse;
import com.aptech.aptech4.notification.api.UnreadCountResponse;
import com.aptech.aptech4.notification.domain.AppNotification;
import com.aptech.aptech4.notification.persistence.AppNotificationRepository;

@Service
public class NotificationQueryService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final AppNotificationRepository notificationRepository;

	public NotificationQueryService(AppNotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> listMine(int page, int size) {
		UUID userId = CurrentUser.requireUserId();
		PageRequest pageable = pageable(page, size);
		Page<AppNotification> result = notificationRepository
				.findByRecipient_IdOrderByCreatedAtDesc(userId, pageable);
		List<NotificationResponse> items = result.getContent().stream()
				.map(this::toResponse)
				.toList();
		return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
	}

	@Transactional(readOnly = true)
	public UnreadCountResponse unreadCount() {
		UUID userId = CurrentUser.requireUserId();
		long count = notificationRepository.countByRecipient_IdAndReadAtIsNull(userId);
		return new UnreadCountResponse(count);
	}

	@Transactional
	public NotificationResponse markRead(UUID notificationId) {
		UUID userId = CurrentUser.requireUserId();
		AppNotification notification = notificationRepository
				.findByIdAndRecipient_Id(notificationId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Notification not found"));
		notification.markRead(Instant.now());
		notificationRepository.saveAndFlush(notification);
		return toResponse(notification);
	}

	@Transactional
	public UnreadCountResponse markAllRead() {
		UUID userId = CurrentUser.requireUserId();
		notificationRepository.markAllRead(userId, Instant.now());
		return unreadCount();
	}

	private PageRequest pageable(int page, int size) {
		if (page < 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "page must be >= 0");
		}
		if (size < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "size must be >= 1");
		}
		if (size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "size must be <= " + MAX_PAGE_SIZE);
		}
		return PageRequest.of(page, size);
	}

	private NotificationResponse toResponse(AppNotification n) {
		return NotificationMapper.toResponse(n);
	}
}
