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
@Table(name = "promotion_campaign_categories")
@IdClass(PromotionCampaignCategoryId.class)
public class PromotionCampaignCategory {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private PromotionCampaign campaign;

	@Id
	@Column(name = "category_id", nullable = false)
	private UUID categoryId;

	protected PromotionCampaignCategory() {
	}

	static PromotionCampaignCategory link(PromotionCampaign campaign, UUID categoryId) {
		PromotionCampaignCategory row = new PromotionCampaignCategory();
		row.campaign = campaign;
		row.categoryId = categoryId;
		return row;
	}

	public PromotionCampaign getCampaign() {
		return campaign;
	}

	public UUID getCategoryId() {
		return categoryId;
	}
}
