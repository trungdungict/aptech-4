package com.aptech.aptech4.order.domain;

import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.aptech.aptech4.identity.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
		name = "checkout_idempotency",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_checkout_idempotency_user_key",
				columnNames = { "user_id", "idempotency_key" }
		)
)
public class CheckoutIdempotency extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private ShopOrder order;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected CheckoutIdempotency() {
	}

	public static CheckoutIdempotency of(UserAccount user, String key, String requestHash, ShopOrder order) {
		CheckoutIdempotency row = new CheckoutIdempotency();
		row.user = user;
		row.idempotencyKey = key;
		row.requestHash = requestHash;
		row.order = order;
		row.createdAt = Instant.now();
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public UserAccount getUser() {
		return user;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public ShopOrder getOrder() {
		return order;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
