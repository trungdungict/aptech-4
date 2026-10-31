package com.aptech.aptech4.promotion.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PromotionCampaignProductId implements Serializable {

	private UUID campaign;
	private UUID productId;

	public PromotionCampaignProductId() {
	}

	public PromotionCampaignProductId(UUID campaign, UUID productId) {
		this.campaign = campaign;
		this.productId = productId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PromotionCampaignProductId that)) {
			return false;
		}
		return Objects.equals(campaign, that.campaign) && Objects.equals(productId, that.productId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(campaign, productId);
	}
}
