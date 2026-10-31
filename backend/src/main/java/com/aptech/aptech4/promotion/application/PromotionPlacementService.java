package com.aptech.aptech4.promotion.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.promotion.api.PromotionPlacementResponse;
import com.aptech.aptech4.promotion.api.PromotionPlacementWriteRequest;
import com.aptech.aptech4.promotion.cache.PromotionCacheInvalidator;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionPlacementRepository;

@Service
public class PromotionPlacementService {

	private final PromotionPlacementRepository placementRepository;
	private final PromotionCampaignRepository campaignRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final PromotionCacheInvalidator promotionCacheInvalidator;

	public PromotionPlacementService(
			PromotionPlacementRepository placementRepository,
			PromotionCampaignRepository campaignRepository,
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			PromotionCacheInvalidator promotionCacheInvalidator
	) {
		this.placementRepository = placementRepository;
		this.campaignRepository = campaignRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.promotionCacheInvalidator = promotionCacheInvalidator;
	}

	@Transactional(readOnly = true)
	public List<PromotionPlacementResponse> listByCampaign(UUID campaignId) {
		requireCampaign(campaignId);
		return placementRepository.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaignId).stream()
				.map(PromotionPlacementResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public PromotionPlacementResponse get(UUID id) {
		return PromotionPlacementResponse.from(require(id));
	}

	@Transactional
	public PromotionPlacementResponse create(UUID campaignId, PromotionPlacementWriteRequest request) {
		PromotionCampaign campaign = requireCampaign(campaignId);
		validateCta(request);
		PromotionPlacement placement = PromotionPlacement.createNew(
				campaign,
				request.placementKey(),
				request.title(),
				request.subtitle(),
				request.ctaLabel(),
				request.ctaType(),
				request.ctaProductId(),
				request.ctaCategoryId(),
				request.ctaCollectionKey(),
				request.ctaUrl(),
				request.desktopImageUrl(),
				request.mobileImageUrl(),
				request.displayOrder() == null ? 0 : request.displayOrder(),
				request.startsAt(),
				request.endsAt(),
				request.enabled() == null || request.enabled()
		);
		placement = placementRepository.saveAndFlush(placement);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionPlacementResponse.from(placement);
	}

	@Transactional
	public PromotionPlacementResponse update(UUID id, PromotionPlacementWriteRequest request) {
		PromotionPlacement placement = require(id);
		validateCta(request);
		placement.applyContent(
				request.placementKey(),
				request.title(),
				request.subtitle(),
				request.ctaLabel(),
				request.ctaType(),
				request.ctaProductId(),
				request.ctaCategoryId(),
				request.ctaCollectionKey(),
				request.ctaUrl(),
				request.desktopImageUrl(),
				request.mobileImageUrl(),
				request.displayOrder() == null ? placement.getDisplayOrder() : request.displayOrder(),
				request.startsAt(),
				request.endsAt(),
				request.enabled() == null ? placement.isEnabled() : request.enabled()
		);
		placement = placementRepository.saveAndFlush(placement);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionPlacementResponse.from(placement);
	}

	@Transactional
	public void delete(UUID id) {
		if (!placementRepository.existsById(id)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Promotion placement not found");
		}
		placementRepository.deleteById(id);
		placementRepository.flush();
		promotionCacheInvalidator.onPromotionMutation();
	}

	private void validateCta(PromotionPlacementWriteRequest request) {
		PromotionCtaType type = request.ctaType() == null ? PromotionCtaType.NONE : request.ctaType();
		switch (type) {
			case PRODUCT -> {
				if (request.ctaProductId() == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "ctaProductId is required for PRODUCT CTA");
				}
				if (!productRepository.existsById(request.ctaProductId())) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "CTA product not found");
				}
			}
			case CATEGORY -> {
				if (request.ctaCategoryId() == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "ctaCategoryId is required for CATEGORY CTA");
				}
				if (!categoryRepository.existsById(request.ctaCategoryId())) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "CTA category not found");
				}
			}
			case COLLECTION -> {
				if (request.ctaCollectionKey() == null || request.ctaCollectionKey().isBlank()) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "ctaCollectionKey is required for COLLECTION CTA");
				}
			}
			case URL -> {
				if (request.ctaUrl() == null || request.ctaUrl().isBlank()) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "ctaUrl is required for URL CTA");
				}
			}
			case NONE -> {
				// no extra fields required
			}
		}
	}

	private PromotionPlacement require(UUID id) {
		return placementRepository.findById(id).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Promotion placement not found")
		);
	}

	private PromotionCampaign requireCampaign(UUID id) {
		return campaignRepository.findByIdWithStrategy(id).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Promotion campaign not found")
		);
	}
}
