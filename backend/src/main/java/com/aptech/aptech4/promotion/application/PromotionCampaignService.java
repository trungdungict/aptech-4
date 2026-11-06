package com.aptech.aptech4.promotion.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.promotion.api.CampaignPreviewRequest;
import com.aptech.aptech4.promotion.api.CampaignPreviewResponse;
import com.aptech.aptech4.promotion.api.PromotionCampaignResponse;
import com.aptech.aptech4.promotion.api.PromotionCampaignWriteRequest;
import com.aptech.aptech4.promotion.api.PromotionEligibilityRequest;
import com.aptech.aptech4.promotion.cache.PromotionCacheInvalidator;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;

@Service
public class PromotionCampaignService {

	private final PromotionCampaignRepository campaignRepository;
	private final PromotionStrategyRepository strategyRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final PromotionCacheInvalidator promotionCacheInvalidator;
	private final PromotionLocalizationService promotionLocalizationService;
	private final Clock clock;

	public PromotionCampaignService(
			PromotionCampaignRepository campaignRepository,
			PromotionStrategyRepository strategyRepository,
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			PromotionCacheInvalidator promotionCacheInvalidator,
			PromotionLocalizationService promotionLocalizationService,
			Clock clock
	) {
		this.campaignRepository = campaignRepository;
		this.strategyRepository = strategyRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.promotionCacheInvalidator = promotionCacheInvalidator;
		this.promotionLocalizationService = promotionLocalizationService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<PromotionCampaignResponse> list() {
		Instant now = clock.instant();
		return campaignRepository.findAllWithStrategyAndPlacements().stream()
				.map(c -> PromotionCampaignResponse.from(c, now))
				.toList();
	}

	@Transactional(readOnly = true)
	public PromotionCampaignResponse get(UUID id) {
		return PromotionCampaignResponse.from(requireFully(id), clock.instant());
	}

	@Transactional
	public PromotionCampaignResponse create(PromotionCampaignWriteRequest request) {
		PromotionStrategy strategy = requireStrategy(request.strategyId());
		String slug = request.slug() == null || request.slug().isBlank()
				? PromotionCampaign.slugify(request.name())
				: PromotionCampaign.slugify(request.slug());
		assertUniqueSlug(slug, null);
		PromotionCampaign campaign = PromotionCampaign.createNew(
				strategy,
				request.name().trim(),
				slug,
				request.startsAt(),
				request.endsAt(),
				request.notes()
		);
		if (request.priority() != null) {
			campaign.setPriority(request.priority());
		}
		if (request.stackable() != null) {
			campaign.setStackable(request.stackable());
		}
		campaign = campaignRepository.saveAndFlush(campaign);
		applyEligibility(campaign, request.productIds(), request.categoryIds(), strategy.getType());
		campaign = campaignRepository.saveAndFlush(campaign);
		promotionLocalizationService.syncCanonicalViCampaign(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(campaign.getId()), clock.instant());
	}

	@Transactional
	public PromotionCampaignResponse update(UUID id, PromotionCampaignWriteRequest request) {
		PromotionCampaign campaign = requireFully(id);
		PromotionStrategy strategy = requireStrategy(request.strategyId());
		String slug = request.slug() == null || request.slug().isBlank()
				? PromotionCampaign.slugify(request.name())
				: PromotionCampaign.slugify(request.slug());
		assertUniqueSlug(slug, id);
		campaign.setStrategy(strategy);
		campaign.setName(request.name().trim());
		campaign.setSlug(slug);
		campaign.setSchedule(request.startsAt(), request.endsAt());
		if (request.priority() != null) {
			campaign.setPriority(request.priority());
		}
		if (request.stackable() != null) {
			campaign.setStackable(request.stackable());
		}
		campaign.setNotes(request.notes());
		applyEligibility(campaign, request.productIds(), request.categoryIds(), strategy.getType());
		campaignRepository.saveAndFlush(campaign);
		promotionLocalizationService.syncCanonicalViCampaign(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(id), clock.instant());
	}

	@Transactional
	public PromotionCampaignResponse replaceEligibility(UUID id, PromotionEligibilityRequest request) {
		PromotionCampaign campaign = requireFully(id);
		applyEligibility(campaign, request.productIds(), request.categoryIds(), campaign.getStrategy().getType());
		campaignRepository.saveAndFlush(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(id), clock.instant());
	}

	@Transactional
	public PromotionCampaignResponse activate(UUID id) {
		PromotionCampaign campaign = requireFully(id);
		Instant now = clock.instant();
		if (now.isAfter(campaign.getEndsAt())) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Cannot activate a campaign after its endsAt"
			);
		}
		campaign.setStatus(PromotionCampaignStatus.ACTIVE);
		campaignRepository.saveAndFlush(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(id), now);
	}

	@Transactional
	public PromotionCampaignResponse pause(UUID id) {
		PromotionCampaign campaign = requireFully(id);
		campaign.setStatus(PromotionCampaignStatus.PAUSED);
		campaignRepository.saveAndFlush(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(id), clock.instant());
	}

	@Transactional
	public PromotionCampaignResponse schedule(UUID id) {
		PromotionCampaign campaign = requireFully(id);
		Instant now = clock.instant();
		if (!campaign.getStartsAt().isAfter(now)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Schedule requires startsAt in the future (or activate instead)"
			);
		}
		campaign.setStatus(PromotionCampaignStatus.SCHEDULED);
		campaignRepository.saveAndFlush(campaign);
		promotionCacheInvalidator.onPromotionMutation();
		return PromotionCampaignResponse.from(requireFully(id), now);
	}

	@Transactional(readOnly = true)
	public CampaignPreviewResponse preview(UUID id, CampaignPreviewRequest request) {
		PromotionCampaign campaign = requireFully(id);
		Instant at = request != null && request.at() != null ? request.at() : clock.instant();
		return new CampaignPreviewResponse(
				at,
				campaign.getStatus(),
				campaign.resolveEffectiveStatus(at),
				campaign.isPricingEffective(at),
				campaign.isPlacementEffective(at),
				"Discount amount calculation is deferred to checkout (P22); this preview reports schedule effectiveness only."
		);
	}

	@Transactional
	public void delete(UUID id) {
		if (!campaignRepository.existsById(id)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Promotion campaign not found");
		}
		campaignRepository.deleteById(id);
		campaignRepository.flush();
		promotionCacheInvalidator.onPromotionMutation();
	}

	/**
	 * Lifecycle hygiene only (ADR-020 R9): {@code ACTIVE → EXPIRED} when {@code clock.instant() > ends_at}.
	 * Idempotent; does not activate SCHEDULED/PAUSED/DRAFT; does not touch order snapshots.
	 * Checkout/public APIs remain authoritative for effectiveness even if this has not run.
	 *
	 * @return number of campaigns transitioned to EXPIRED
	 */
	@Transactional
	public int expireDueCampaigns() {
		Instant now = clock.instant();
		List<PromotionCampaign> due = campaignRepository.findActivePastEnd(
				PromotionCampaignStatus.ACTIVE,
				now
		);
		if (due.isEmpty()) {
			return 0;
		}
		for (PromotionCampaign campaign : due) {
			campaign.setStatus(PromotionCampaignStatus.EXPIRED);
		}
		campaignRepository.saveAll(due);
		campaignRepository.flush();
		promotionCacheInvalidator.onPromotionMutation();
		return due.size();
	}

	private void applyEligibility(
			PromotionCampaign campaign,
			List<UUID> productIds,
			List<UUID> categoryIds,
			PromotionStrategyType type
	) {
		Set<UUID> products = productIds == null ? Set.of() : new HashSet<>(productIds);
		Set<UUID> categories = categoryIds == null ? Set.of() : new HashSet<>(categoryIds);
		for (UUID productId : products) {
			if (!productRepository.existsById(productId)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Product not found: " + productId);
			}
		}
		for (UUID categoryId : categories) {
			if (!categoryRepository.existsById(categoryId)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Category not found: " + categoryId);
			}
		}
		if (type == PromotionStrategyType.PRODUCT_DISCOUNT && products.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "PRODUCT_DISCOUNT requires at least one productId");
		}
		if (type == PromotionStrategyType.CATEGORY_DISCOUNT && categories.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "CATEGORY_DISCOUNT requires at least one categoryId");
		}
		campaign.replaceProductIds(products);
		campaign.replaceCategoryIds(categories);
	}

	private PromotionCampaign requireFully(UUID id) {
		return campaignRepository.findByIdFully(id).orElseThrow(() ->
				ApiException.keyed(HttpStatus.NOT_FOUND, "Not Found", "api.error.not_found.campaign")
		);
	}

	private PromotionStrategy requireStrategy(UUID id) {
		return strategyRepository.findById(id).orElseThrow(() ->
				new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Promotion strategy not found")
		);
	}

	private void assertUniqueSlug(String slug, UUID excludeId) {
		boolean exists = excludeId == null
				? campaignRepository.existsBySlugIgnoreCase(slug)
				: campaignRepository.existsBySlugIgnoreCaseAndIdNot(slug, excludeId);
		if (exists) {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Promotion campaign slug already exists");
		}
	}
}
