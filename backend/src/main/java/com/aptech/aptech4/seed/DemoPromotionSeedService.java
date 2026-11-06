package com.aptech.aptech4.seed;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.catalog.upload.ProductImageProperties;
import com.aptech.aptech4.catalog.upload.UploadPurpose;
import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.promotion.application.PromotionLocalizationService;
import com.aptech.aptech4.promotion.cache.PromotionCacheInvalidator;
import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionCtaType;
import com.aptech.aptech4.promotion.domain.PromotionPlacement;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;
import com.aptech.aptech4.promotion.persistence.PromotionCampaignRepository;
import com.aptech.aptech4.promotion.persistence.PromotionStrategyRepository;
import com.aptech.aptech4.seed.DemoPromotionCatalog.CampaignSlug;
import com.aptech.aptech4.seed.DemoPromotionCatalog.DisplayName;
import com.aptech.aptech4.seed.DemoPromotionCatalog.StrategyCode;

/**
 * Idempotent grocery promotion fixtures keyed by strategy {@code code} and campaign {@code slug}.
 * Does not create orders — checkout discounts are demonstrated against live cart + seeded campaigns.
 */
@Service
public class DemoPromotionSeedService {

	private static final Logger log = LoggerFactory.getLogger(DemoPromotionSeedService.class);

	/** Former Phase-8 1×1 PNG path — removed when writing the real hero JPG. */
	private static final String RETIRED_HERO_FILENAME = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png";

	private final PromotionStrategyRepository strategyRepository;
	private final PromotionCampaignRepository campaignRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final Clock clock;
	private final ObjectProvider<ProductImageProperties> productImageProperties;
	private final ObjectProvider<PromotionCacheInvalidator> promotionCacheInvalidator;
	private final PromotionLocalizationService promotionLocalizationService;

	public DemoPromotionSeedService(
			PromotionStrategyRepository strategyRepository,
			PromotionCampaignRepository campaignRepository,
			ProductRepository productRepository,
			CategoryRepository categoryRepository,
			Clock clock,
			ObjectProvider<ProductImageProperties> productImageProperties,
			ObjectProvider<PromotionCacheInvalidator> promotionCacheInvalidator,
			PromotionLocalizationService promotionLocalizationService
	) {
		this.strategyRepository = strategyRepository;
		this.campaignRepository = campaignRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.clock = clock;
		this.productImageProperties = productImageProperties;
		this.promotionCacheInvalidator = promotionCacheInvalidator;
		this.promotionLocalizationService = promotionLocalizationService;
	}

	public record Counts(int strategiesCreated, int strategiesUpdated, int campaignsCreated, int campaignsUpdated) {
	}

	@Transactional
	public Counts seedAll() {
		ensureHeroImageFile();

		Instant now = clock.instant();
		Instant activeStart = now.minus(2, ChronoUnit.DAYS);
		Instant activeEnd = now.plus(30, ChronoUnit.DAYS);
		Instant scheduledStart = now.plus(7, ChronoUnit.DAYS);
		Instant scheduledEnd = now.plus(14, ChronoUnit.DAYS);
		Instant expiredStart = now.minus(40, ChronoUnit.DAYS);
		Instant expiredEnd = now.minus(2, ChronoUnit.DAYS);

		Product apple = requireProduct(DemoPromotionCatalog.PRODUCT_SKU_APPLE);
		Product banana = requireProduct(DemoPromotionCatalog.PRODUCT_SKU_BANANA);
		Category beverages = requireCategory(DemoPromotionCatalog.CATEGORY_SLUG_BEVERAGES);
		Category fruits = requireCategory(DemoPromotionCatalog.CATEGORY_SLUG_FRUITS);

		int stratCreated = 0;
		int stratUpdated = 0;
		int campCreated = 0;
		int campUpdated = 0;

		UpsertStrat pct = upsertStrategy(
				StrategyCode.PCT_ORDER.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.PCT_ORDER),
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"),
				null,
				null,
				null,
				null,
				false,
				100,
				true
		);
		if (pct.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		// Pricing campaigns default PAUSED so seed-demo does not change checkout totals until activated.
		UpsertCamp pctCamp = upsertCampaign(
				CampaignSlug.PCT_ACTIVE.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.PCT_ACTIVE),
				pct.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate for order-level % (empty eligibility = all lines)",
				Set.of(),
				Set.of()
		);
		if (pctCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat fixed = upsertStrategy(
				StrategyCode.FIXED_ORDER.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.FIXED_ORDER),
				PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null,
				// USD of record (was ₫15,000 / min ₫50,000 at mock 1 USD = 25_000 VND).
				new BigDecimal("0.60"),
				null,
				new BigDecimal("2.00"),
				null,
				false,
				90,
				true
		);
		if (fixed.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp fixedCamp = upsertCampaign(
				CampaignSlug.FIXED_ACTIVE.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.FIXED_ACTIVE),
				fixed.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate for fixed-amount (minOrderAmount 50k)",
				Set.of(),
				Set.of()
		);
		if (fixedCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat product = upsertStrategy(
				StrategyCode.PRODUCT.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.PRODUCT),
				PromotionStrategyType.PRODUCT_DISCOUNT,
				new BigDecimal("15.00"),
				null,
				null,
				null,
				null,
				false,
				80,
				true
		);
		if (product.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp productCamp = upsertCampaign(
				CampaignSlug.PRODUCT_APPLE.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.PRODUCT_APPLE),
				product.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate; PRODUCT_DISCOUNT = apple SKU only",
				Set.of(apple.getId()),
				Set.of()
		);
		if (productCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat category = upsertStrategy(
				StrategyCode.CATEGORY.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.CATEGORY),
				PromotionStrategyType.CATEGORY_DISCOUNT,
				new BigDecimal("8.00"),
				null,
				null,
				null,
				null,
				false,
				70,
				true
		);
		if (category.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp categoryCamp = upsertCampaign(
				CampaignSlug.CATEGORY_BEVERAGES.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.CATEGORY_BEVERAGES),
				category.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate; CATEGORY_DISCOUNT = food-beverages",
				Set.of(),
				Set.of(beverages.getId())
		);
		if (categoryCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat orderOr = upsertStrategy(
				StrategyCode.ORDER_OR.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.ORDER_OR),
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("5.00"),
				null,
				null,
				null,
				null,
				false,
				60,
				true
		);
		if (orderOr.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp orderOrCamp = upsertCampaign(
				CampaignSlug.ORDER_OR.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.ORDER_OR),
				orderOr.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate; order-level both sets → product OR category",
				Set.of(apple.getId(), banana.getId()),
				Set.of(beverages.getId(), fruits.getId())
		);
		if (orderOrCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat stackA = upsertStrategy(
				StrategyCode.STACK_A.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.STACK_A),
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("5.00"),
				null,
				null,
				null,
				null,
				true,
				200,
				true
		);
		if (stackA.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp stackACamp = upsertCampaign(
				CampaignSlug.STACK_A.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.STACK_A),
				stackA.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate with stack B for stacking demo",
				Set.of(),
				Set.of()
		);
		if (stackACamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat stackB = upsertStrategy(
				StrategyCode.STACK_B.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.STACK_B),
				PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null,
				// USD of record (was ₫5,000 at mock 1 USD = 25_000 VND).
				new BigDecimal("0.20"),
				null,
				null,
				null,
				true,
				100,
				true
		);
		if (stackB.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp stackBCamp = upsertCampaign(
				CampaignSlug.STACK_B.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.STACK_B),
				stackB.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"PAUSED by default — activate with stack A for stacking demo",
				Set.of(),
				Set.of()
		);
		if (stackBCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertStrat banner = upsertStrategy(
				StrategyCode.BANNER.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.BANNER),
				PromotionStrategyType.BANNER_ONLY,
				null,
				null,
				null,
				null,
				null,
				false,
				10,
				true
		);
		if (banner.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}
		UpsertCamp heroCamp = upsertCampaign(
				CampaignSlug.HERO.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.HERO),
				banner.strategy(),
				PromotionCampaignStatus.ACTIVE,
				activeStart,
				activeEnd,
				"HOME_HERO placement for storefront showcase",
				Set.of(),
				Set.of()
		);
		if (heroCamp.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}
		ensureHeroPlacement(heroCamp.campaign());

		UpsertStrat lifecycle = upsertStrategy(
				StrategyCode.LIFECYCLE.code(),
				DemoPromotionCatalog.strategyName(StrategyCode.LIFECYCLE),
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("3.00"),
				null,
				null,
				null,
				null,
				false,
				20,
				true
		);
		if (lifecycle.created()) {
			stratCreated++;
		}
		else {
			stratUpdated++;
		}

		UpsertCamp scheduled = upsertCampaign(
				CampaignSlug.SCHEDULED.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.SCHEDULED),
				lifecycle.strategy(),
				PromotionCampaignStatus.SCHEDULED,
				scheduledStart,
				scheduledEnd,
				"Not yet effective",
				Set.of(),
				Set.of()
		);
		if (scheduled.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertCamp paused = upsertCampaign(
				CampaignSlug.PAUSED.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.PAUSED),
				lifecycle.strategy(),
				PromotionCampaignStatus.PAUSED,
				activeStart,
				activeEnd,
				"Paused — not public / not priced",
				Set.of(),
				Set.of()
		);
		if (paused.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertCamp draft = upsertCampaign(
				CampaignSlug.DRAFT.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.DRAFT),
				lifecycle.strategy(),
				PromotionCampaignStatus.DRAFT,
				activeStart,
				activeEnd,
				"Draft — not public",
				Set.of(),
				Set.of()
		);
		if (draft.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		UpsertCamp expired = upsertCampaign(
				CampaignSlug.EXPIRED.slug(),
				DemoPromotionCatalog.campaignName(CampaignSlug.EXPIRED),
				lifecycle.strategy(),
				PromotionCampaignStatus.EXPIRED,
				expiredStart,
				expiredEnd,
				"Expired window + EXPIRED status",
				Set.of(),
				Set.of()
		);
		if (expired.created()) {
			campCreated++;
		}
		else {
			campUpdated++;
		}

		PromotionCacheInvalidator invalidator = promotionCacheInvalidator.getIfAvailable();
		if (invalidator != null) {
			invalidator.onPromotionMutation();
		}

		Counts counts = new Counts(stratCreated, stratUpdated, campCreated, campUpdated);
		log.info(
				"Demo promotion seed: strategies +{}/~{} campaigns +{}/~{}",
				counts.strategiesCreated(),
				counts.strategiesUpdated(),
				counts.campaignsCreated(),
				counts.campaignsUpdated()
		);
		return counts;
	}

	private Product requireProduct(String sku) {
		return productRepository.findBySkuIgnoreCase(sku)
				.orElseThrow(() -> new IllegalStateException(
						"Demo promotion seed requires product SKU " + sku + " — run FoodMart catalog seed first"
				));
	}

	private Category requireCategory(String slug) {
		return categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new IllegalStateException(
						"Demo promotion seed requires category slug " + slug + " — run FoodMart catalog seed first"
				));
	}

	private record UpsertStrat(PromotionStrategy strategy, boolean created) {
	}

	private record UpsertCamp(PromotionCampaign campaign, boolean created) {
	}

	private UpsertStrat upsertStrategy(
			String code,
			DisplayName displayName,
			PromotionStrategyType type,
			BigDecimal discountPercent,
			BigDecimal discountAmount,
			BigDecimal maxDiscountAmount,
			BigDecimal minOrderAmount,
			Integer minQuantity,
			boolean stackable,
			int priority,
			boolean active
	) {
		String name = displayName.vi();
		var existing = strategyRepository.findByCodeIgnoreCase(code);
		PromotionStrategy strategy;
		boolean created;
		if (existing.isPresent()) {
			strategy = existing.get();
			strategy.applyUpdate(
					name,
					code,
					type,
					discountPercent,
					discountAmount,
					maxDiscountAmount,
					minOrderAmount,
					minQuantity,
					stackable,
					priority,
					active
			);
			strategy = strategyRepository.saveAndFlush(strategy);
			created = false;
		}
		else {
			strategy = PromotionStrategy.createNew(
					name,
					code,
					type,
					discountPercent,
					discountAmount,
					maxDiscountAmount,
					minOrderAmount,
					minQuantity,
					stackable,
					priority,
					active
			);
			strategy = strategyRepository.saveAndFlush(strategy);
			created = true;
		}
		promotionLocalizationService.upsertStrategy(strategy, AppLocale.VI.code(), displayName.vi());
		promotionLocalizationService.upsertStrategy(strategy, AppLocale.EN.code(), displayName.en());
		return new UpsertStrat(strategy, created);
	}

	private UpsertCamp upsertCampaign(
			String slug,
			DisplayName displayName,
			PromotionStrategy strategy,
			PromotionCampaignStatus status,
			Instant startsAt,
			Instant endsAt,
			String notes,
			Set<UUID> productIds,
			Set<UUID> categoryIds
	) {
		String name = displayName.vi();
		var existing = campaignRepository.findBySlugFully(slug);
		PromotionCampaign campaign;
		boolean created;
		if (existing.isPresent()) {
			campaign = existing.get();
			campaign.setStrategy(strategy);
			campaign.setName(name);
			campaign.setSchedule(startsAt, endsAt);
			campaign.setStatus(status);
			campaign.setPriority(strategy.getPriority());
			campaign.setStackable(strategy.isStackable());
			campaign.setNotes(notes);
			campaign.replaceProductIds(productIds);
			campaign.replaceCategoryIds(categoryIds);
			campaign = campaignRepository.saveAndFlush(campaign);
			created = false;
		}
		else {
			campaign = PromotionCampaign.createNew(strategy, name, slug, startsAt, endsAt, notes);
			campaign.setStatus(status);
			campaign.replaceProductIds(productIds);
			campaign.replaceCategoryIds(categoryIds);
			campaign = campaignRepository.saveAndFlush(campaign);
			created = true;
		}
		promotionLocalizationService.upsertCampaign(campaign, AppLocale.VI.code(), displayName.vi());
		promotionLocalizationService.upsertCampaign(campaign, AppLocale.EN.code(), displayName.en());
		return new UpsertCamp(campaign, created);
	}

	private void ensureHeroPlacement(PromotionCampaign campaign) {
		PromotionCampaign managed = campaignRepository.findBySlugFully(campaign.getSlug()).orElse(campaign);
		PromotionPlacement hero = managed.getPlacements().stream()
				.filter(p -> p.getPlacementKey() == PromotionPlacementKey.HOME_HERO)
				.findFirst()
				.orElse(null);
		PromotionPlacement placement;
		if (hero == null) {
			placement = PromotionPlacement.createNew(
					managed,
					PromotionPlacementKey.HOME_HERO,
					DemoPromotionCatalog.HERO_TITLE,
					DemoPromotionCatalog.HERO_SUBTITLE,
					DemoPromotionCatalog.HERO_CTA_VI,
					PromotionCtaType.NONE,
					null,
					null,
					null,
					null,
					DemoPromotionCatalog.HERO_IMAGE_PUBLIC_PATH,
					null,
					1,
					null,
					null,
					true
			);
		}
		else {
			hero.applyContent(
					PromotionPlacementKey.HOME_HERO,
					DemoPromotionCatalog.HERO_TITLE,
					DemoPromotionCatalog.HERO_SUBTITLE,
					DemoPromotionCatalog.HERO_CTA_VI,
					PromotionCtaType.NONE,
					null,
					null,
					null,
					null,
					DemoPromotionCatalog.HERO_IMAGE_PUBLIC_PATH,
					null,
					1,
					null,
					null,
					true
			);
			placement = hero;
		}
		campaignRepository.saveAndFlush(managed);
		PromotionPlacement persisted = campaignRepository.findBySlugFully(managed.getSlug()).orElse(managed)
				.getPlacements().stream()
				.filter(p -> p.getPlacementKey() == PromotionPlacementKey.HOME_HERO)
				.findFirst()
				.orElse(placement);
		promotionLocalizationService.upsertPlacement(
				persisted,
				AppLocale.VI.code(),
				DemoPromotionCatalog.HERO_TITLE,
				DemoPromotionCatalog.HERO_SUBTITLE,
				DemoPromotionCatalog.HERO_CTA_VI
		);
		promotionLocalizationService.upsertPlacement(
				persisted,
				AppLocale.EN.code(),
				DemoPromotionCatalog.HERO_TITLE_EN,
				DemoPromotionCatalog.HERO_SUBTITLE_EN,
				DemoPromotionCatalog.HERO_CTA_EN
		);
	}

	private void ensureHeroImageFile() {
		ProductImageProperties props = productImageProperties.getIfAvailable();
		if (props == null) {
			log.warn("ProductImageProperties unavailable — skipping demo hero image file write");
			return;
		}
		Path dir = props.uploadDirectory(UploadPurpose.PROMOTION);
		Path file = dir.resolve(DemoPromotionCatalog.HERO_IMAGE_FILENAME);
		Path retired = dir.resolve(RETIRED_HERO_FILENAME);
		try {
			Files.createDirectories(dir);
			ClassPathResource resource = new ClassPathResource(DemoPromotionCatalog.HERO_IMAGE_CLASSPATH);
			if (!resource.exists()) {
				throw new IllegalStateException(
						"Missing classpath resource " + DemoPromotionCatalog.HERO_IMAGE_CLASSPATH
				);
			}
			try (InputStream in = resource.getInputStream()) {
				Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
			}
			Files.deleteIfExists(retired);
			log.info("Wrote demo promotion hero image {}", file);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot write demo promotion hero image to " + file, ex);
		}
	}
}
