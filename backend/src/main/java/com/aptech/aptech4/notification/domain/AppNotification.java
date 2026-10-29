package com.aptech.aptech4.notification.domain;

import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.order.domain.ShopOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class AppNotification extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_user_id", nullable = false)
	private UserAccount recipient;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 64)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "message", nullable = false, length = 1000)
	private String message;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private ShopOrder order;

	@Column(name = "dedupe_key", nullable = false, length = 160)
	private String dedupeKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "read_at")
	private Instant readAt;

	protected AppNotification() {
	}

	public static AppNotification create(
			UserAccount recipient,
			NotificationType type,
			String title,
			String message,
			ShopOrder order,
			String dedupeKey
	) {
		AppNotification n = new AppNotification();
		n.recipient = recipient;
		n.type = type;
		n.title = title;
		n.message = message;
		n.order = order;
		n.dedupeKey = dedupeKey;
		n.createdAt = Instant.now();
		return n;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public UserAccount getRecipient() {
		return recipient;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public ShopOrder getOrder() {
		return order;
	}

	public String getDedupeKey() {
		return dedupeKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public boolean isUnread() {
		return readAt == null;
	}

	public void markRead(Instant at) {
		if (this.readAt == null) {
			this.readAt = at;
		}
	}
}
