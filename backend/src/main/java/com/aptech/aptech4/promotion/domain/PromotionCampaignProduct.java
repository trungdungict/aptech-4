package com.aptech.aptech4.promotion.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_campaign_products")
@IdClass(PromotionCampaignProductId.class)
public class PromotionCampaignProduct {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private PromotionCampaign campaign;

	@Id
	@Column(name = "product_id", nullable = false)
	private UUID productId;

	protected PromotionCampaignProduct() {
	}

	static PromotionCampaignProduct link(PromotionCampaign campaign, UUID productId) {
		PromotionCampaignProduct row = new PromotionCampaignProduct();
		row.campaign = campaign;
		row.productId = productId;
		return row;
	}

	public PromotionCampaign getCampaign() {
		return campaign;
	}

	public UUID getProductId() {
		return productId;
	}
}
