package com.aptech.aptech4.promotion.api;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PromotionPlacementResponse")
public record PromotionPlacementResponse(
		UUID id,
		UUID campaignId,
		PromotionPlacementKey placementKey,
		String title,
		String subtitle,
		String ctaLabel,
		PromotionCtaType ctaType,
		UUID ctaProductId,
		UUID ctaCategoryId,
		String ctaCollectionKey,
		String ctaUrl,
		String desktopImageUrl,
		String mobileImageUrl,
		int displayOrder,
		Instant startsAt,
		Instant endsAt,
		boolean enabled,
		Instant createdAt,
		Instant updatedAt
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static PromotionPlacementResponse from(PromotionPlacement placement) {
		return new PromotionPlacementResponse(
				placement.getId(),
				placement.getCampaign().getId(),
				placement.getPlacementKey(),
				placement.getTitle(),
				placement.getSubtitle(),
				placement.getCtaLabel(),
				placement.getCtaType(),
				placement.getCtaProductId(),
				placement.getCtaCategoryId(),
				placement.getCtaCollectionKey(),
				placement.getCtaUrl(),
				placement.getDesktopImageUrl(),
				placement.getMobileImageUrl(),
				placement.getDisplayOrder(),
				placement.getStartsAt(),
				placement.getEndsAt(),
				placement.isEnabled(),
				placement.getCreatedAt(),
				placement.getUpdatedAt()
		);
	}
}
