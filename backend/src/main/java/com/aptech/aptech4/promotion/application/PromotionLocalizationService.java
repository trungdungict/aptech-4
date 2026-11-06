package com.aptech.aptech4.promotion.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.common.locale.ContentLocalization;
import com.aptech.aptech4.common.locale.RequestLocaleHolder;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignTranslation;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementTranslation;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyTranslation;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignTranslationRepository;
import com.aptech.aptech4.promotion.persistence.PromotionPlacementTranslationRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyTranslationRepository;

@Service
public class PromotionLocalizationService {

	public record PlacementText(String title, String subtitle, String ctaLabel) {
	}

	private final PromotionPlacementTranslationRepository placementTranslationRepository;
	private final PromotionCampaignTranslationRepository campaignTranslationRepository;
	private final PromotionStrategyTranslationRepository strategyTranslationRepository;

	public PromotionLocalizationService(
			PromotionPlacementTranslationRepository placementTranslationRepository,
			PromotionCampaignTranslationRepository campaignTranslationRepository,
			PromotionStrategyTranslationRepository strategyTranslationRepository
	) {
		this.placementTranslationRepository = placementTranslationRepository;
		this.campaignTranslationRepository = campaignTranslationRepository;
		this.strategyTranslationRepository = strategyTranslationRepository;
	}

	@Transactional(readOnly = true)
	public PlacementText placementText(PromotionPlacement placement) {
		return placementText(placement, RequestLocaleHolder.get());
	}

	@Transactional(readOnly = true)
	public PlacementText placementText(PromotionPlacement placement, AppLocale locale) {
		PromotionPlacementTranslation row = placementTranslationRepository
				.findByPlacementIdAndLocale(placement.getId(), locale.code())
				.orElse(null);
		String title = ContentLocalization.resolveName(row == null ? null : row.getTitle(), placement.getTitle());
		String subtitle = row == null ? placement.getSubtitle() : row.getSubtitle();
		String ctaLabel = row == null ? placement.getCtaLabel() : row.getCtaLabel();
		return new PlacementText(title, subtitle, ctaLabel);
	}

	@Transactional(readOnly = true)
	public Map<UUID, PlacementText> placementTexts(Collection<PromotionPlacement> placements, AppLocale locale) {
		if (placements == null || placements.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = placements.stream().map(PromotionPlacement::getId).toList();
		Map<UUID, PromotionPlacementTranslation> byId = placementTranslationRepository
				.findByPlacementIdInAndLocale(ids, locale.code())
				.stream()
				.collect(Collectors.toMap(t -> t.getPlacement().getId(), Function.identity(), (a, b) -> a));
		Map<UUID, PlacementText> out = new HashMap<>();
		for (PromotionPlacement placement : placements) {
			PromotionPlacementTranslation row = byId.get(placement.getId());
			out.put(
					placement.getId(),
					new PlacementText(
							ContentLocalization.resolveName(row == null ? null : row.getTitle(), placement.getTitle()),
							row == null ? placement.getSubtitle() : row.getSubtitle(),
							row == null ? placement.getCtaLabel() : row.getCtaLabel()
					)
			);
		}
		return out;
	}

	@Transactional(readOnly = true)
	public String strategyName(PromotionStrategy strategy) {
		return strategyName(strategy, RequestLocaleHolder.get());
	}

	@Transactional(readOnly = true)
	public String strategyName(PromotionStrategy strategy, AppLocale locale) {
		return strategyTranslationRepository.findByStrategyIdAndLocale(strategy.getId(), locale.code())
				.map(PromotionStrategyTranslation::getName)
				.filter(n -> n != null && !n.isBlank())
				.orElse(strategy.getName());
	}

	@Transactional(readOnly = true)
	public String campaignName(PromotionCampaign campaign) {
		return campaignName(campaign, RequestLocaleHolder.get());
	}

	@Transactional(readOnly = true)
	public String campaignName(PromotionCampaign campaign, AppLocale locale) {
		return campaignTranslationRepository.findByCampaignIdAndLocale(campaign.getId(), locale.code())
				.map(PromotionCampaignTranslation::getName)
				.filter(n -> n != null && !n.isBlank())
				.orElse(campaign.getName());
	}

	@Transactional
	public void syncCanonicalViPlacement(PromotionPlacement placement) {
		upsertPlacement(placement, AppLocale.VI.code(), placement.getTitle(), placement.getSubtitle(), placement.getCtaLabel());
	}

	@Transactional
	public void syncCanonicalViCampaign(PromotionCampaign campaign) {
		upsertCampaign(campaign, AppLocale.VI.code(), campaign.getName());
	}

	@Transactional
	public void syncCanonicalViStrategy(PromotionStrategy strategy) {
		upsertStrategy(strategy, AppLocale.VI.code(), strategy.getName());
	}

	@Transactional
	public void upsertPlacement(
			PromotionPlacement placement,
			String locale,
			String title,
			String subtitle,
			String ctaLabel
	) {
		PromotionPlacementTranslation row = placementTranslationRepository
				.findByPlacementIdAndLocale(placement.getId(), locale)
				.orElseGet(() -> PromotionPlacementTranslation.createNew(placement, locale, title, subtitle, ctaLabel));
		row.setTitle(title);
		row.setSubtitle(subtitle);
		row.setCtaLabel(ctaLabel);
		placementTranslationRepository.save(row);
	}

	@Transactional
	public void upsertCampaign(PromotionCampaign campaign, String locale, String name) {
		PromotionCampaignTranslation row = campaignTranslationRepository
				.findByCampaignIdAndLocale(campaign.getId(), locale)
				.orElseGet(() -> PromotionCampaignTranslation.createNew(campaign, locale, name));
		row.setName(name);
		campaignTranslationRepository.save(row);
	}

	@Transactional
	public void upsertStrategy(PromotionStrategy strategy, String locale, String name) {
		PromotionStrategyTranslation row = strategyTranslationRepository
				.findByStrategyIdAndLocale(strategy.getId(), locale)
				.orElseGet(() -> PromotionStrategyTranslation.createNew(strategy, locale, name));
		row.setName(name);
		strategyTranslationRepository.save(row);
	}

	@Transactional(readOnly = true)
	public Map<String, PlacementText> placementTranslationMap(PromotionPlacement placement) {
		Map<String, PlacementText> map = new HashMap<>();
		map.put(AppLocale.VI.code(), new PlacementText(placement.getTitle(), placement.getSubtitle(), placement.getCtaLabel()));
		map.put(AppLocale.EN.code(), new PlacementText(null, null, null));
		for (PromotionPlacementTranslation row : placementTranslationRepository.findByPlacementId(placement.getId())) {
			map.put(row.getLocale(), new PlacementText(row.getTitle(), row.getSubtitle(), row.getCtaLabel()));
		}
		map.put(AppLocale.VI.code(), new PlacementText(placement.getTitle(), placement.getSubtitle(), placement.getCtaLabel()));
		return map;
	}

	@Transactional(readOnly = true)
	public Map<String, String> campaignNameMap(PromotionCampaign campaign) {
		Map<String, String> map = new HashMap<>();
		map.put(AppLocale.VI.code(), campaign.getName());
		map.put(AppLocale.EN.code(), null);
		for (PromotionCampaignTranslation row : campaignTranslationRepository.findByCampaignId(campaign.getId())) {
			map.put(row.getLocale(), row.getName());
		}
		map.put(AppLocale.VI.code(), campaign.getName());
		return map;
	}

	@Transactional(readOnly = true)
	public Map<String, String> strategyNameMap(PromotionStrategy strategy) {
		Map<String, String> map = new HashMap<>();
		map.put(AppLocale.VI.code(), strategy.getName());
		map.put(AppLocale.EN.code(), null);
		for (PromotionStrategyTranslation row : strategyTranslationRepository.findByStrategyId(strategy.getId())) {
			map.put(row.getLocale(), row.getName());
		}
		map.put(AppLocale.VI.code(), strategy.getName());
		return map;
	}
}
