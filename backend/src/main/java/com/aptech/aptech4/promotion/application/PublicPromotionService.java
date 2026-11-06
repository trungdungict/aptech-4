package com.aptech.aptech4.promotion.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.locale.RequestLocaleHolder;
import com.aptech.aptech4.promotion.api.HomepagePromotionsResponse;
import com.aptech.aptech4.promotion.api.PublicPlacementResponse;
import com.aptech.aptech4.promotion.cache.PromotionCacheNames;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;

/**
 * Public placement reads with short-TTL Redis cache (ADR-020 R7 / D8 / D9).
 * Empty responses are intentional — storefront applies static FoodMart fallback (P20).
 * Cache keys include request locale (P34-T04).
 */
@Service
public class PublicPromotionService {

	private final PromotionCampaignRepository campaignRepository;
	private final PromotionLocalizationService promotionLocalizationService;
	private final Clock clock;

	public PublicPromotionService(
			PromotionCampaignRepository campaignRepository,
			PromotionLocalizationService promotionLocalizationService,
			Clock clock
	) {
		this.campaignRepository = campaignRepository;
		this.promotionLocalizationService = promotionLocalizationService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	@Cacheable(
			cacheNames = PromotionCacheNames.HOMEPAGE,
			key = "T(com.aptech.aptech4.promotion.cache.PromotionCacheKeys).homepage()"
	)
	public HomepagePromotionsResponse homepage() {
		List<PublicPlacementResponse> hero = loadVisible(EnumSet.of(PromotionPlacementKey.HOME_HERO));
		PublicPlacementResponse promoTop = firstOrNull(loadVisible(EnumSet.of(PromotionPlacementKey.HOME_PROMO_TOP)));
		PublicPlacementResponse promoBottom = firstOrNull(loadVisible(EnumSet.of(PromotionPlacementKey.HOME_PROMO_BOTTOM)));
		if (hero.isEmpty() && promoTop == null && promoBottom == null) {
			return HomepagePromotionsResponse.empty();
		}
		return new HomepagePromotionsResponse(hero, promoTop, promoBottom);
	}

	@Transactional(readOnly = true)
	@Cacheable(
			cacheNames = PromotionCacheNames.PLACEMENTS,
			key = "T(com.aptech.aptech4.promotion.cache.PromotionCacheKeys).placements(#keys)"
	)
	public List<PublicPlacementResponse> listPlacements(List<PromotionPlacementKey> keys) {
		Set<PromotionPlacementKey> filter = EnumSet.noneOf(PromotionPlacementKey.class);
		if (keys != null) {
			for (PromotionPlacementKey key : keys) {
				if (key != null) {
					filter.add(key);
				}
			}
		}
		if (filter.isEmpty()) {
			filter = EnumSet.allOf(PromotionPlacementKey.class);
		}
		return loadVisible(filter);
	}

	private List<PublicPlacementResponse> loadVisible(Set<PromotionPlacementKey> keys) {
		Instant now = clock.instant();
		List<PromotionCampaign> candidates = campaignRepository.findPlacementEffectiveCandidates(
				PromotionCampaignStatus.ACTIVE,
				now
		);
		List<PromotionPlacement> visible = new ArrayList<>();
		for (PromotionCampaign campaign : candidates) {
			if (!campaign.isPlacementEffective(now)) {
				continue;
			}
			for (PromotionPlacement placement : campaign.getPlacements()) {
				if (!keys.contains(placement.getPlacementKey())) {
					continue;
				}
				if (!placement.isVisibleAt(now)) {
					continue;
				}
				visible.add(placement);
			}
		}
		visible.sort(Comparator
				.comparingInt(PromotionPlacement::getDisplayOrder)
				.thenComparing(PromotionPlacement::getId));
		Map<UUID, PromotionLocalizationService.PlacementText> texts =
				promotionLocalizationService.placementTexts(visible, RequestLocaleHolder.get());
		return visible.stream()
				.map(placement -> {
					PromotionLocalizationService.PlacementText text = texts.get(placement.getId());
					if (text == null) {
						return PublicPlacementResponse.from(placement);
					}
					return PublicPlacementResponse.from(placement, text.title(), text.subtitle(), text.ctaLabel());
				})
				.toList();
	}

	private static PublicPlacementResponse firstOrNull(List<PublicPlacementResponse> items) {
		return items.isEmpty() ? null : items.getFirst();
	}
}
