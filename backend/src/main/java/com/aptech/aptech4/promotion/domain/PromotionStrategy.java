package com.aptech.aptech4.promotion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_strategies")
public class PromotionStrategy extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "code", length = 64)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private PromotionStrategyType type;

	@Column(name = "discount_percent", precision = 5, scale = 2)
	private BigDecimal discountPercent;

	@Column(name = "discount_amount", precision = 12, scale = 2)
	private BigDecimal discountAmount;

	@Column(name = "max_discount_amount", precision = 12, scale = 2)
	private BigDecimal maxDiscountAmount;

	@Column(name = "min_order_amount", precision = 12, scale = 2)
	private BigDecimal minOrderAmount;

	@Column(name = "min_quantity")
	private Integer minQuantity;

	@Column(name = "stackable", nullable = false)
	private boolean stackable;

	@Column(name = "priority", nullable = false)
	private int priority = 100;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected PromotionStrategy() {
	}

	public static PromotionStrategy createNew(
			String name,
			String code,
			PromotionStrategyType type,
			BigDecimal discountPercent,
			BigDecimal discountAmount,
			BigDecimal maxDiscountAmount,
			BigDecimal minOrderAmount,
			Integer minQuantity,
			boolean stackable,
			int priority,
			boolean active
	) {
		PromotionStrategyRules.validateWrite(
				type, discountPercent, discountAmount, maxDiscountAmount, minOrderAmount, minQuantity
		);
		PromotionStrategy strategy = new PromotionStrategy();
		strategy.name = name;
		strategy.code = code;
		strategy.type = type;
		strategy.discountPercent = discountPercent;
		strategy.discountAmount = discountAmount;
		strategy.maxDiscountAmount = maxDiscountAmount;
		strategy.minOrderAmount = minOrderAmount;
		strategy.minQuantity = minQuantity;
		strategy.stackable = stackable;
		strategy.priority = priority;
		strategy.active = active;
		Instant now = Instant.now();
		strategy.createdAt = now;
		strategy.updatedAt = now;
		return strategy;
	}

	public void applyUpdate(
			String name,
			String code,
			PromotionStrategyType type,
			BigDecimal discountPercent,
			BigDecimal discountAmount,
			BigDecimal maxDiscountAmount,
			BigDecimal minOrderAmount,
			Integer minQuantity,
			boolean stackable,
			int priority,
			boolean active
	) {
		PromotionStrategyRules.validateWrite(
				type, discountPercent, discountAmount, maxDiscountAmount, minOrderAmount, minQuantity
		);
		this.name = name;
		this.code = code;
		this.type = type;
		this.discountPercent = discountPercent;
		this.discountAmount = discountAmount;
		this.maxDiscountAmount = maxDiscountAmount;
		this.minOrderAmount = minOrderAmount;
		this.minQuantity = minQuantity;
		this.stackable = stackable;
		this.priority = priority;
		this.active = active;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCode() {
		return code;
	}

	public PromotionStrategyType getType() {
		return type;
	}

	public BigDecimal getDiscountPercent() {
		return discountPercent;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getMaxDiscountAmount() {
		return maxDiscountAmount;
	}

	public BigDecimal getMinOrderAmount() {
		return minOrderAmount;
	}

	public Integer getMinQuantity() {
		return minQuantity;
	}

	public boolean isStackable() {
		return stackable;
	}

	public int getPriority() {
		return priority;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
		this.updatedAt = Instant.now();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
