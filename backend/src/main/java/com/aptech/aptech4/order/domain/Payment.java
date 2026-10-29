package com.aptech.aptech4.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private ShopOrder order;

	@Column(name = "provider", nullable = false, length = 32)
	private String provider = "MOCK";

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private PaymentStatus status;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "provider_reference", length = 120)
	private String providerReference;

	@Column(name = "failure_reason", length = 255)
	private String failureReason;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Payment() {
	}

	public static Payment mockPending(ShopOrder order, BigDecimal amount, String currency) {
		Payment payment = new Payment();
		payment.order = order;
		payment.provider = "MOCK";
		payment.status = PaymentStatus.PENDING;
		payment.amount = amount;
		payment.currency = currency;
		Instant now = Instant.now();
		payment.createdAt = now;
		payment.updatedAt = now;
		return payment;
	}

	public void touch() {
		this.updatedAt = Instant.now();
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public ShopOrder getOrder() {
		return order;
	}

	public void setOrder(ShopOrder order) {
		this.order = order;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getProviderReference() {
		return providerReference;
	}

	public void setProviderReference(String providerReference) {
		this.providerReference = providerReference;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "Payment{id=" + id + ", status=" + status + ", amount=" + amount + '}';
	}
}
