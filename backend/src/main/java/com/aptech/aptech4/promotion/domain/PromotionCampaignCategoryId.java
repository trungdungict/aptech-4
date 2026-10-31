package com.aptech.aptech4.promotion.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PromotionCampaignCategoryId implements Serializable {

	private UUID campaign;
	private UUID categoryId;

	public PromotionCampaignCategoryId() {
	}

	public PromotionCampaignCategoryId(UUID campaign, UUID categoryId) {
		this.campaign = campaign;
		this.categoryId = categoryId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PromotionCampaignCategoryId that)) {
			return false;
		}
		return Objects.equals(campaign, that.campaign) && Objects.equals(categoryId, that.categoryId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(campaign, categoryId);
	}
}
