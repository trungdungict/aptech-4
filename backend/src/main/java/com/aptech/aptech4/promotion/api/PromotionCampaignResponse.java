package com.aptech.aptech4.promotion.api;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignCategory;
import com.aptech.aptech4.promotion.domain.PromotionCampaignProduct;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PromotionCampaignResponse")
public record PromotionCampaignResponse(
		UUID id,
		String name,
		String slug,
		PromotionCampaignStatus status,
		PromotionCampaignStatus effectiveStatus,
		boolean pricingEffective,
		boolean placementEffective,
		Instant startsAt,
		Instant endsAt,
		int priority,
		boolean stackable,
		String notes,
		PromotionStrategySummary strategy,
		List<UUID> productIds,
		List<UUID> categoryIds,
		int placementCount,
		Instant createdAt,
		Instant updatedAt
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public record PromotionStrategySummary(
			UUID id,
			String name,
			String code,
			String type,
			boolean active
	) implements Serializable {
		@Serial
		private static final long serialVersionUID = 1L;
	}

	public static PromotionCampaignResponse from(PromotionCampaign campaign, Instant now) {
		PromotionStrategy strategy = campaign.getStrategy();
		List<UUID> productIds = campaign.getProducts().stream()
				.map(PromotionCampaignProduct::getProductId)
				.sorted()
				.toList();
		List<UUID> categoryIds = campaign.getCategories().stream()
				.map(PromotionCampaignCategory::getCategoryId)
				.sorted()
				.toList();
		return new PromotionCampaignResponse(
				campaign.getId(),
				campaign.getName(),
				campaign.getSlug(),
				campaign.getStatus(),
				campaign.resolveEffectiveStatus(now),
				campaign.isPricingEffective(now),
				campaign.isPlacementEffective(now),
				campaign.getStartsAt(),
				campaign.getEndsAt(),
				campaign.getPriority(),
				campaign.isStackable(),
				campaign.getNotes(),
				new PromotionStrategySummary(
						strategy.getId(),
						strategy.getName(),
						strategy.getCode(),
						strategy.getType().name(),
						strategy.isActive()
				),
				productIds,
				categoryIds,
				campaign.getPlacements().size(),
				campaign.getCreatedAt(),
				campaign.getUpdatedAt()
		);
	}
}
