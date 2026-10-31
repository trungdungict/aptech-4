package com.aptech.aptech4.promotion.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PublicPlacementResponse")
public record PublicPlacementResponse(
		UUID id,
		UUID campaignId,
		PromotionPlacementKey placementKey,
		String title,
		String subtitle,
		String ctaLabel,
		PublicCta cta,
		String desktopImageUrl,
		String mobileImageUrl,
		int displayOrder
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public record PublicCta(
			PromotionCtaType type,
			UUID productId,
			UUID categoryId,
			String collectionKey,
			String url,
			String href
	) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;
	}

	public static PublicPlacementResponse from(PromotionPlacement placement) {
		PromotionCtaType type = placement.getCtaType() == null ? PromotionCtaType.NONE : placement.getCtaType();
		String href = switch (type) {
			case PRODUCT -> placement.getCtaProductId() == null
					? null
					: "/products/" + placement.getCtaProductId();
			case CATEGORY -> placement.getCtaCategoryId() == null
					? null
					: "/products?categoryId=" + placement.getCtaCategoryId();
			case COLLECTION -> placement.getCtaCollectionKey() == null || placement.getCtaCollectionKey().isBlank()
					? null
					: "/products?collection=" + placement.getCtaCollectionKey();
			case URL -> placement.getCtaUrl();
			case NONE -> null;
		};
		return new PublicPlacementResponse(
				placement.getId(),
				placement.getCampaign().getId(),
				placement.getPlacementKey(),
				placement.getTitle(),
				placement.getSubtitle(),
				placement.getCtaLabel(),
				new PublicCta(
						type,
						placement.getCtaProductId(),
						placement.getCtaCategoryId(),
						placement.getCtaCollectionKey(),
						placement.getCtaUrl(),
						href
				),
				placement.getDesktopImageUrl(),
				placement.getMobileImageUrl(),
				placement.getDisplayOrder()
		);
	}
}
