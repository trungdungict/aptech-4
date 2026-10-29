package com.aptech.aptech4.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.aptech.aptech4.identity.domain.UserAccount;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_reservations")
public class StockReservation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "idempotency_key", length = 100)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private ReservationStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<StockReservationItem> items = new ArrayList<>();

	protected StockReservation() {
	}

	public static StockReservation createActive(
			UserAccount user,
			String idempotencyKey,
			Instant expiresAt
	) {
		StockReservation reservation = new StockReservation();
		reservation.user = user;
		reservation.idempotencyKey = idempotencyKey;
		reservation.status = ReservationStatus.ACTIVE;
		reservation.expiresAt = expiresAt;
		Instant now = Instant.now();
		reservation.createdAt = now;
		reservation.updatedAt = now;
		return reservation;
	}

	public void addItem(StockReservationItem item) {
		items.add(item);
		item.setReservation(this);
	}

	public void touch() {
		this.updatedAt = Instant.now();
	}

	public boolean isExpired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UserAccount getUser() {
		return user;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public ReservationStatus getStatus() {
		return status;
	}

	public void setStatus(ReservationStatus status) {
		this.status = status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public List<StockReservationItem> getItems() {
		return items;
	}

	@Override
	public String toString() {
		return "StockReservation{id=" + id + ", status=" + status + '}';
	}
}
