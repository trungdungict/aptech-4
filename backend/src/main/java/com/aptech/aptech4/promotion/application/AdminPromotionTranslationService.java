package com.aptech.aptech4.promotion.application;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.promotion.api.NameTranslationsResponse;
import com.aptech.aptech4.promotion.api.NameTranslationsUpdateRequest;
import com.aptech.aptech4.promotion.api.PlacementLocaleTextRequest;
import com.aptech.aptech4.promotion.api.PlacementTranslationsResponse;
import com.aptech.aptech4.promotion.api.PlacementTranslationsUpdateRequest;
import com.aptech.aptech4.promotion.cache.PromotionCacheInvalidator;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionPlacementRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;

@Service
public class AdminPromotionTranslationService {

	private final PromotionStrategyRepository strategyRepository;
	private final PromotionCampaignRepository campaignRepository;
	private final PromotionPlacementRepository placementRepository;
	private final PromotionLocalizationService localizationService;
	private final PromotionCacheInvalidator promotionCacheInvalidator;

	public AdminPromotionTranslationService(
			PromotionStrategyRepository strategyRepository,
			PromotionCampaignRepository campaignRepository,
			PromotionPlacementRepository placementRepository,
			PromotionLocalizationService localizationService,
			PromotionCacheInvalidator promotionCacheInvalidator
	) {
		this.strategyRepository = strategyRepository;
		this.campaignRepository = campaignRepository;
		this.placementRepository = placementRepository;
		this.localizationService = localizationService;
		this.promotionCacheInvalidator = promotionCacheInvalidator;
	}

	@Transactional(readOnly = true)
	public NameTranslationsResponse getStrategyTranslations(UUID id) {
		PromotionStrategy strategy = requireStrategy(id);
		return new NameTranslationsResponse(id, localizationService.strategyNameMap(strategy));
	}

	@Transactional
	public NameTranslationsResponse putStrategyTranslations(UUID id, NameTranslationsUpdateRequest request) {
		PromotionStrategy strategy = requireStrategy(id);
		String viName = requireViName(request.translations());
		strategy.applyUpdate(
				viName,
				strategy.getCode(),
				strategy.getType(),
				strategy.getDiscountPercent(),
				strategy.getDiscountAmount(),
				strategy.getMaxDiscountAmount(),
				strategy.getMinOrderAmount(),
				strategy.getMinQuantity(),
				strategy.isStackable(),
				strategy.getPriority(),
				strategy.isActive()
		);
		strategy = strategyRepository.saveAndFlush(strategy);
		localizationService.upsertStrategy(strategy, AppLocale.VI.code(), strategy.getName());
		String enName = request.translations().get(AppLocale.EN.code());
		if (enName != null && !enName.isBlank()) {
			localizationService.upsertStrategy(strategy, AppLocale.EN.code(), enName.trim());
		}
		promotionCacheInvalidator.onPromotionMutation();
		return getStrategyTranslations(id);
	}

	@Transactional(readOnly = true)
	public NameTranslationsResponse getCampaignTranslations(UUID id) {
		PromotionCampaign campaign = requireCampaign(id);
		return new NameTranslationsResponse(id, localizationService.campaignNameMap(campaign));
	}

	@Transactional
	public NameTranslationsResponse putCampaignTranslations(UUID id, NameTranslationsUpdateRequest request) {
		PromotionCampaign campaign = requireCampaign(id);
		String viName = requireViName(request.translations());
		campaign.setName(viName);
		campaign = campaignRepository.saveAndFlush(campaign);
		localizationService.upsertCampaign(campaign, AppLocale.VI.code(), campaign.getName());
		String enName = request.translations().get(AppLocale.EN.code());
		if (enName != null && !enName.isBlank()) {
			localizationService.upsertCampaign(campaign, AppLocale.EN.code(), enName.trim());
		}
		promotionCacheInvalidator.onPromotionMutation();
		return getCampaignTranslations(id);
	}

	@Transactional(readOnly = true)
	public PlacementTranslationsResponse getPlacementTranslations(UUID id) {
		PromotionPlacement placement = requirePlacement(id);
		return new PlacementTranslationsResponse(id, localizationService.placementTranslationMap(placement));
	}

	@Transactional
	public PlacementTranslationsResponse putPlacementTranslations(UUID id, PlacementTranslationsUpdateRequest request) {
		PromotionPlacement placement = requirePlacement(id);
		PlacementLocaleTextRequest vi = requireViPlacement(request.translations());
		placement.applyContent(
				placement.getPlacementKey(),
				normalize(vi.title()),
				blankToNull(vi.subtitle()),
				blankToNull(vi.ctaLabel()),
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
				placement.isEnabled()
		);
		placement = placementRepository.saveAndFlush(placement);
		localizationService.upsertPlacement(
				placement,
				AppLocale.VI.code(),
				placement.getTitle(),
				placement.getSubtitle(),
				placement.getCtaLabel()
		);
		PlacementLocaleTextRequest en = request.translations().get(AppLocale.EN.code());
		if (en != null && en.title() != null && !en.title().isBlank()) {
			localizationService.upsertPlacement(
					placement,
					AppLocale.EN.code(),
					normalize(en.title()),
					blankToNull(en.subtitle()),
					blankToNull(en.ctaLabel())
			);
		}
		promotionCacheInvalidator.onPromotionMutation();
		return getPlacementTranslations(id);
	}

	private String requireViName(Map<String, String> translations) {
		if (translations == null || !translations.containsKey(AppLocale.VI.code())) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		for (String locale : translations.keySet()) {
			assertSupportedLocale(locale);
		}
		String vi = translations.get(AppLocale.VI.code());
		if (vi == null || vi.isBlank()) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		return vi.trim();
	}

	private PlacementLocaleTextRequest requireViPlacement(Map<String, PlacementLocaleTextRequest> translations) {
		if (translations == null || !translations.containsKey(AppLocale.VI.code())) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		for (String locale : translations.keySet()) {
			assertSupportedLocale(locale);
		}
		PlacementLocaleTextRequest vi = translations.get(AppLocale.VI.code());
		if (vi == null || vi.title() == null || vi.title().isBlank()) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.translations_vi_required");
		}
		return vi;
	}

	private static void assertSupportedLocale(String locale) {
		String normalized = locale == null ? "" : locale.trim().toLowerCase(Locale.ROOT);
		if (AppLocale.fromLanguageCode(normalized) == null) {
			throw ApiException.keyed(HttpStatus.BAD_REQUEST, "Bad Request", "api.error.bad_request.locale");
		}
	}

	private PromotionStrategy requireStrategy(UUID id) {
		return strategyRepository.findById(id).orElseThrow(() ->
				ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.strategy")
		);
	}

	private PromotionCampaign requireCampaign(UUID id) {
		return campaignRepository.findById(id).orElseThrow(() ->
				ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.campaign")
		);
	}

	private PromotionPlacement requirePlacement(UUID id) {
		return placementRepository.findById(id).orElseThrow(() ->
				ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.placement")
		);
	}

	private static String normalize(String value) {
		return value.trim().replaceAll("\\s+", " ");
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
