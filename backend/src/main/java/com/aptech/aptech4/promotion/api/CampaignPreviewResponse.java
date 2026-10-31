package com.aptech.aptech4.promotion.api;

import java.time.Instant;

import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CampaignPreviewResponse")
public record CampaignPreviewResponse(
		Instant evaluatedAt,
		PromotionCampaignStatus status,
		PromotionCampaignStatus effectiveStatus,
		boolean pricingEffective,
		boolean placementEffective,
		String note
) {
}
