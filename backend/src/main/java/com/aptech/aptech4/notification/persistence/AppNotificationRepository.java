package com.aptech.aptech4.notification.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.notification.domain.AppNotification;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

	Page<AppNotification> findByRecipient_IdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

	long countByRecipient_IdAndReadAtIsNull(UUID recipientUserId);

	Optional<AppNotification> findByIdAndRecipient_Id(UUID id, UUID recipientUserId);

	boolean existsByRecipient_IdAndDedupeKey(UUID recipientUserId, String dedupeKey);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update AppNotification n
			set n.readAt = :readAt
			where n.recipient.id = :recipientId and n.readAt is null
			""")
	int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
