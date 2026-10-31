package com.aptech.aptech4.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Denormalized promotion application record for an order (ADR-020). Survives campaign/strategy edits/deletes.
 */
@Entity
@Table(name = "order_promotion_snapshots")
public class OrderPromotionSnapshot extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private ShopOrder order;

	@Column(name = "campaign_id")
	private UUID campaignId;

	@Column(name = "strategy_id")
	private UUID strategyId;

	@Column(name = "campaign_name", nullable = false, length = 160)
	private String campaignName;

	@Column(name = "campaign_slug", nullable = false, length = 160)
	private String campaignSlug;

	@Column(name = "strategy_name", nullable = false, length = 120)
	private String strategyName;

	@Column(name = "strategy_type", nullable = false, length = 40)
	private String strategyType;

	@Column(name = "strategy_code", length = 64)
	private String strategyCode;

	@Column(name = "priority", nullable = false)
	private int priority;

	@Column(name = "stackable", nullable = false)
	private boolean stackable;

	@Column(name = "discount_percent", precision = 5, scale = 2)
	private BigDecimal discountPercent;

	@Column(name = "discount_amount_rule", precision = 12, scale = 2)
	private BigDecimal discountAmountRule;

	@Column(name = "max_discount_amount", precision = 12, scale = 2)
	private BigDecimal maxDiscountAmount;

	@Column(name = "eligible_subtotal", nullable = false, precision = 12, scale = 2)
	private BigDecimal eligibleSubtotal;

	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt = Instant.now();

	protected OrderPromotionSnapshot() {
	}

	public static OrderPromotionSnapshot capture(
			UUID campaignId,
			UUID strategyId,
			String campaignName,
			String campaignSlug,
			String strategyName,
			String strategyType,
			String strategyCode,
			int priority,
			boolean stackable,
			BigDecimal discountPercent,
			BigDecimal discountAmountRule,
			BigDecimal maxDiscountAmount,
			BigDecimal eligibleSubtotal,
			BigDecimal discountAmount,
			String currency
	) {
		OrderPromotionSnapshot snapshot = new OrderPromotionSnapshot();
		snapshot.campaignId = campaignId;
		snapshot.strategyId = strategyId;
		snapshot.campaignName = campaignName;
		snapshot.campaignSlug = campaignSlug;
		snapshot.strategyName = strategyName;
		snapshot.strategyType = strategyType;
		snapshot.strategyCode = strategyCode;
		snapshot.priority = priority;
		snapshot.stackable = stackable;
		snapshot.discountPercent = discountPercent;
		snapshot.discountAmountRule = discountAmountRule;
		snapshot.maxDiscountAmount = maxDiscountAmount;
		snapshot.eligibleSubtotal = eligibleSubtotal;
		snapshot.discountAmount = discountAmount;
		snapshot.currency = currency;
		snapshot.appliedAt = Instant.now();
		return snapshot;
	}

	void setOrder(ShopOrder order) {
		this.order = order;
	}

	public UUID getId() {
		return id;
	}

	public ShopOrder getOrder() {
		return order;
	}

	public UUID getCampaignId() {
		return campaignId;
	}

	public UUID getStrategyId() {
		return strategyId;
	}

	public String getCampaignName() {
		return campaignName;
	}

	public String getCampaignSlug() {
		return campaignSlug;
	}

	public String getStrategyName() {
		return strategyName;
	}

	public String getStrategyType() {
		return strategyType;
	}

	public String getStrategyCode() {
		return strategyCode;
	}

	public int getPriority() {
		return priority;
	}

	public boolean isStackable() {
		return stackable;
	}

	public BigDecimal getDiscountPercent() {
		return discountPercent;
	}

	public BigDecimal getDiscountAmountRule() {
		return discountAmountRule;
	}

	public BigDecimal getMaxDiscountAmount() {
		return maxDiscountAmount;
	}

	public BigDecimal getEligibleSubtotal() {
		return eligibleSubtotal;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public String getCurrency() {
		return currency;
	}

	public Instant getAppliedAt() {
		return appliedAt;
	}
}
