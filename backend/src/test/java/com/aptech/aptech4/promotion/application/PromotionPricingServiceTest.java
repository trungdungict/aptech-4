package com.aptech.aptech4.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignStatus;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;

class PromotionPricingServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
	private static final Instant WINDOW_START = Instant.parse("2026-11-01T00:00:00Z");
	private static final Instant WINDOW_END = Instant.parse("2026-11-30T00:00:00Z");

	private static final UUID PRODUCT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
	private static final UUID PRODUCT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final UUID CATEGORY_FOOD = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
	private static final UUID CATEGORY_OTHER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

	private PromotionPricingService service;

	@BeforeEach
	void setUp() {
		service = new PromotionPricingService(Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void noApplicableCampaign_returnsZeroDiscount() {
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 2, "200.00"));
		PromotionPricingResult result = service.price(cart, List.of());

		assertThat(result.subtotalAmount()).isEqualByComparingTo("200.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
		assertThat(result.appliedPromotions()).isEmpty();
		assertThat(result.promotionSummary()).isNull();
	}

	@Test
	void fixedAmountDiscount_appliesCappedAtEligibleBase() {
		PromotionCampaign campaign = activeCampaign(
				fixedStrategy("10.00", false, 100),
				"Fixed10",
				"fixed-10",
				100,
				false
		);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "50.00"));

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
		assertThat(result.appliedPromotions()).hasSize(1);
		assertThat(result.appliedPromotions().getFirst().discountAmount()).isEqualByComparingTo("10.00");
		assertThat(result.appliedPromotions().getFirst().eligibleSubtotal()).isEqualByComparingTo("50.00");
	}

	@Test
	void percentageDiscount_appliesHalfUpRounding() {
		PromotionCampaign campaign = activeCampaign(
				percentStrategy("10.00", false, 100),
				"Pct10",
				"pct-10",
				100,
				false
		);
		// 33.33 * 10% = 3.333 → 3.33 HALF_UP
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "33.33"));

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.discountAmount()).isEqualByComparingTo("3.33");
	}

	@Test
	void percentageRounding_halfUpFromFive() {
		PromotionCampaign campaign = activeCampaign(
				percentStrategy("15.00", false, 100),
				"Pct15",
				"pct-15",
				100,
				false
		);
		// 10.00 * 15% = 1.50 exact
		assertThat(service.price(List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "10.00")), List.of(campaign))
				.discountAmount()).isEqualByComparingTo("1.50");

		// 10.01 * 15% = 1.5015 → 1.50 HALF_UP? 1.5015 to 2dp = 1.50
		assertThat(service.price(List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "10.01")), List.of(campaign))
				.discountAmount()).isEqualByComparingTo("1.50");

		// 10.03 * 15% = 1.5045 → 1.50
		assertThat(service.price(List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "10.03")), List.of(campaign))
				.discountAmount()).isEqualByComparingTo("1.50");

		// 10.04 * 15% = 1.506 → 1.51
		assertThat(service.price(List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "10.04")), List.of(campaign))
				.discountAmount()).isEqualByComparingTo("1.51");
	}

	@Test
	void acS1_twoNonStackable_appliesHigherPriorityOnly() {
		PromotionCampaign low = activeCampaign(
				percentStrategy("10.00", false, 50),
				"Low",
				"low",
				50,
				false
		);
		PromotionCampaign high = activeCampaign(
				percentStrategy("20.00", false, 200),
				"High",
				"high",
				200,
				false
		);
		setId(low, UUID.fromString("11111111-1111-1111-1111-111111111111"));
		setId(high, UUID.fromString("22222222-2222-2222-2222-222222222222"));

		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));
		PromotionPricingResult result = service.price(cart, List.of(low, high));

		assertThat(result.appliedPromotions()).hasSize(1);
		assertThat(result.appliedPromotions().getFirst().campaignSlug()).isEqualTo("high");
		assertThat(result.discountAmount()).isEqualByComparingTo("20.00");
	}

	@Test
	void acS9_samePriority_earlierStartsAtWinsNonStackable() {
		PromotionCampaign later = campaignWithWindow(
				percentStrategy("10.00", false, 100),
				"Later",
				"later",
				100,
				false,
				Instant.parse("2026-11-10T00:00:00Z"),
				WINDOW_END
		);
		PromotionCampaign earlier = campaignWithWindow(
				percentStrategy("25.00", false, 100),
				"Earlier",
				"earlier",
				100,
				false,
				Instant.parse("2026-11-05T00:00:00Z"),
				WINDOW_END
		);
		setId(later, UUID.fromString("33333333-3333-3333-3333-333333333333"));
		setId(earlier, UUID.fromString("44444444-4444-4444-4444-444444444444"));

		PromotionPricingResult result = service.price(
				List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00")),
				List.of(later, earlier)
		);

		assertThat(result.appliedPromotions()).hasSize(1);
		assertThat(result.appliedPromotions().getFirst().campaignSlug()).isEqualTo("earlier");
		assertThat(result.discountAmount()).isEqualByComparingTo("25.00");
	}

	@Test
	void acS2_twoStackable_bothApplyNonCompounding() {
		PromotionCampaign a = activeCampaign(
				percentStrategy("10.00", true, 100),
				"StackA",
				"stack-a",
				100,
				true
		);
		PromotionCampaign b = activeCampaign(
				fixedStrategy("5.00", true, 90),
				"StackB",
				"stack-b",
				90,
				true
		);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingResult result = service.price(cart, List.of(a, b));

		assertThat(result.appliedPromotions()).hasSize(2);
		assertThat(result.discountAmount()).isEqualByComparingTo("15.00");
	}

	@Test
	void acS3_oneNonStackablePlusStackable_bothApply() {
		PromotionCampaign nonStack = activeCampaign(
				percentStrategy("10.00", false, 100),
				"NonStack",
				"non-stack",
				100,
				false
		);
		PromotionCampaign stack = activeCampaign(
				fixedStrategy("5.00", true, 50),
				"Stack",
				"stack",
				50,
				true
		);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingResult result = service.price(cart, List.of(nonStack, stack));

		assertThat(result.appliedPromotions()).hasSize(2);
		assertThat(result.discountAmount()).isEqualByComparingTo("15.00");
		assertThat(result.promotionSummary()).isEqualTo("NonStack, Stack");
	}

	@Test
	void acS4_productDiscount_ignoresNonMatchingLines() {
		PromotionCampaign campaign = activeCampaign(
				productPercentStrategy("50.00", false, 100),
				"ProductSale",
				"product-sale",
				100,
				false
		);
		campaign.replaceProductIds(Set.of(PRODUCT_A));

		List<PricingCartLine> cart = List.of(
				line(PRODUCT_A, CATEGORY_FOOD, 1, "40.00"),
				line(PRODUCT_B, CATEGORY_OTHER, 1, "60.00")
		);

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.subtotalAmount()).isEqualByComparingTo("100.00");
		assertThat(result.appliedPromotions()).hasSize(1);
		assertThat(result.appliedPromotions().getFirst().eligibleSubtotal()).isEqualByComparingTo("40.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("20.00");
	}

	@Test
	void acS4_categoryDiscount_usesCategoryMembership() {
		PromotionCampaign campaign = activeCampaign(
				categoryFixedStrategy("8.00", false, 100),
				"CatSale",
				"cat-sale",
				100,
				false
		);
		campaign.replaceCategoryIds(Set.of(CATEGORY_FOOD));

		List<PricingCartLine> cart = List.of(
				line(PRODUCT_A, CATEGORY_FOOD, 1, "30.00"),
				line(PRODUCT_B, CATEGORY_OTHER, 1, "70.00")
		);

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.appliedPromotions().getFirst().eligibleSubtotal()).isEqualByComparingTo("30.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("8.00");
	}

	@Test
	void acS5_orderLevelEmptyEligibility_appliesToFullSubtotal() {
		PromotionCampaign campaign = activeCampaign(
				percentStrategy("10.00", false, 100),
				"AllCart",
				"all-cart",
				100,
				false
		);
		List<PricingCartLine> cart = List.of(
				line(PRODUCT_A, CATEGORY_FOOD, 1, "40.00"),
				line(PRODUCT_B, CATEGORY_OTHER, 1, "60.00")
		);

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.appliedPromotions().getFirst().eligibleSubtotal()).isEqualByComparingTo("100.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
	}

	@Test
	void orderLevelBothEligibilitySets_matchesProductOrCategory() {
		PromotionCampaign campaign = activeCampaign(
				percentStrategy("10.00", false, 100),
				"OrMatch",
				"or-match",
				100,
				false
		);
		campaign.replaceProductIds(Set.of(PRODUCT_A));
		campaign.replaceCategoryIds(Set.of(CATEGORY_OTHER));

		List<PricingCartLine> cart = List.of(
				line(PRODUCT_A, CATEGORY_FOOD, 1, "40.00"),   // product hit
				line(PRODUCT_B, CATEGORY_OTHER, 1, "60.00"),  // category hit
				line(UUID.randomUUID(), CATEGORY_FOOD, 1, "100.00") // neither
		);

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.appliedPromotions().getFirst().eligibleSubtotal()).isEqualByComparingTo("100.00");
		assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
	}

	@Test
	void acS6_maxDiscountAmount_truncatesCampaignContribution() {
		PromotionStrategy strategy = PromotionStrategy.createNew(
				"CappedPct",
				null,
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("50.00"),
				null,
				new BigDecimal("12.00"),
				null,
				null,
				false,
				100,
				true
		);
		PromotionCampaign campaign = activeCampaign(strategy, "Capped", "capped", 100, false);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.discountAmount()).isEqualByComparingTo("12.00");
	}

	@Test
	void acS7_totalDiscountNeverExceedsSubtotal() {
		PromotionCampaign a = activeCampaign(
				fixedStrategy("80.00", true, 100),
				"BigA",
				"big-a",
				100,
				true
		);
		PromotionCampaign b = activeCampaign(
				fixedStrategy("80.00", true, 90),
				"BigB",
				"big-b",
				90,
				true
		);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingResult result = service.price(cart, List.of(a, b));

		assertThat(result.discountAmount()).isEqualByComparingTo("100.00");
		assertThat(result.discountAmount()).isLessThanOrEqualTo(result.subtotalAmount());
		BigDecimal snapshotSum = result.appliedPromotions().stream()
				.map(AppliedPromotion::discountAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(snapshotSum).isEqualByComparingTo(result.discountAmount());
	}

	@Test
	void acS8_bannerOnly_neverAffectsDiscount() {
		PromotionStrategy banner = PromotionStrategy.createNew(
				"Banner", null, PromotionStrategyType.BANNER_ONLY,
				null, null, null, null, null, false, 100, true
		);
		PromotionCampaign campaign = activeCampaign(banner, "Hero", "hero", 100, false);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingResult result = service.price(cart, List.of(campaign));

		assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
		assertThat(result.appliedPromotions()).isEmpty();
	}

	@Test
	void inactivePausedNotStartedExpired_excluded() {
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionCampaign paused = activeCampaign(
				percentStrategy("10.00", false, 100), "Paused", "paused", 100, false
		);
		paused.setStatus(PromotionCampaignStatus.PAUSED);

		PromotionCampaign draft = activeCampaign(
				percentStrategy("10.00", false, 100), "Draft", "draft", 100, false
		);
		draft.setStatus(PromotionCampaignStatus.DRAFT);

		PromotionCampaign notStarted = campaignWithWindow(
				percentStrategy("10.00", false, 100),
				"Future",
				"future",
				100,
				false,
				Instant.parse("2026-11-20T00:00:00Z"),
				Instant.parse("2026-11-25T00:00:00Z")
		);

		PromotionCampaign expiredWindow = campaignWithWindow(
				percentStrategy("10.00", false, 100),
				"Past",
				"past",
				100,
				false,
				Instant.parse("2026-10-01T00:00:00Z"),
				Instant.parse("2026-10-31T00:00:00Z")
		);

		PromotionStrategy inactiveStrategy = percentStrategy("10.00", false, 100);
		inactiveStrategy.setActive(false);
		PromotionCampaign inactive = activeCampaign(inactiveStrategy, "InactiveStrat", "inactive-strat", 100, false);

		PromotionPricingResult result = service.price(
				cart,
				List.of(paused, draft, notStarted, expiredWindow, inactive)
		);

		assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
		assertThat(result.appliedPromotions()).isEmpty();
	}

	@Test
	void utcBoundary_inclusiveStartAndEnd() {
		PromotionCampaign campaign = campaignWithWindow(
				percentStrategy("10.00", false, 100),
				"Bound",
				"bound",
				100,
				false,
				Instant.parse("2026-11-10T00:00:00Z"),
				Instant.parse("2026-11-20T00:00:00Z")
		);
		List<PricingCartLine> cart = List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00"));

		PromotionPricingService atStart = new PromotionPricingService(
				Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC)
		);
		PromotionPricingService beforeStart = new PromotionPricingService(
				Clock.fixed(Instant.parse("2026-11-09T23:59:59Z"), ZoneOffset.UTC)
		);
		PromotionPricingService atEnd = new PromotionPricingService(
				Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneOffset.UTC)
		);
		PromotionPricingService afterEnd = new PromotionPricingService(
				Clock.fixed(Instant.parse("2026-11-20T00:00:01Z"), ZoneOffset.UTC)
		);

		assertThat(atStart.price(cart, List.of(campaign)).discountAmount()).isEqualByComparingTo("10.00");
		assertThat(beforeStart.price(cart, List.of(campaign)).discountAmount()).isEqualByComparingTo("0.00");
		assertThat(atEnd.price(cart, List.of(campaign)).discountAmount()).isEqualByComparingTo("10.00");
		assertThat(afterEnd.price(cart, List.of(campaign)).discountAmount()).isEqualByComparingTo("0.00");
	}

	@Test
	void emptyCart_andZeroSubtotal_returnZero() {
		PromotionCampaign campaign = activeCampaign(
				percentStrategy("10.00", false, 100), "Any", "any", 100, false
		);

		assertThat(service.price(List.of(), List.of(campaign)).discountAmount()).isEqualByComparingTo("0.00");
		assertThat(service.price(
				List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "0.00")),
				List.of(campaign)
		).discountAmount()).isEqualByComparingTo("0.00");
	}

	@Test
	void productDiscount_withNoMatchingLines_yieldsZero() {
		PromotionCampaign campaign = activeCampaign(
				productPercentStrategy("50.00", false, 100),
				"Miss",
				"miss",
				100,
				false
		);
		campaign.replaceProductIds(Set.of(PRODUCT_A));

		PromotionPricingResult result = service.price(
				List.of(line(PRODUCT_B, CATEGORY_OTHER, 1, "100.00")),
				List.of(campaign)
		);

		assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
		assertThat(result.appliedPromotions()).isEmpty();
	}

	@Test
	void minOrderAmount_andMinQuantity_evaluatedAgainstFullCart() {
		PromotionStrategy strategy = PromotionStrategy.createNew(
				"Mins",
				null,
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal("10.00"),
				null,
				null,
				new BigDecimal("150.00"),
				5,
				false,
				100,
				true
		);
		PromotionCampaign campaign = activeCampaign(strategy, "Mins", "mins", 100, false);

		PromotionPricingResult tooSmall = service.price(
				List.of(line(PRODUCT_A, CATEGORY_FOOD, 2, "100.00")),
				List.of(campaign)
		);
		assertThat(tooSmall.discountAmount()).isEqualByComparingTo("0.00");

		// qty 5, subtotal 210.00 — mins pass against full cart
		PromotionPricingResult ok = service.price(
				List.of(
						line(PRODUCT_A, CATEGORY_FOOD, 3, "150.00"),
						line(PRODUCT_B, CATEGORY_OTHER, 2, "60.00")
				),
				List.of(campaign)
		);
		assertThat(ok.subtotalAmount()).isEqualByComparingTo("210.00");
		assertThat(ok.discountAmount()).isEqualByComparingTo("21.00");
	}

	@Test
	void zeroDiscountDoesNotConsumeNonStackableSlot() {
		PromotionStrategy zeroFixed = PromotionStrategy.createNew(
				"Zero",
				null,
				PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null,
				new BigDecimal("0.00"),
				null,
				null,
				null,
				false,
				200,
				true
		);
		PromotionCampaign zero = activeCampaign(zeroFixed, "Zero", "zero", 200, false);
		PromotionCampaign next = activeCampaign(
				percentStrategy("10.00", false, 100), "Next", "next", 100, false
		);

		PromotionPricingResult result = service.price(
				List.of(line(PRODUCT_A, CATEGORY_FOOD, 1, "100.00")),
				List.of(zero, next)
		);

		assertThat(result.appliedPromotions()).hasSize(1);
		assertThat(result.appliedPromotions().getFirst().campaignSlug()).isEqualTo("next");
	}

	private static PricingCartLine line(UUID productId, UUID categoryId, int qty, String lineTotal) {
		return new PricingCartLine(productId, categoryId, qty, new BigDecimal(lineTotal));
	}

	private static PromotionStrategy percentStrategy(String percent, boolean stackable, int priority) {
		return PromotionStrategy.createNew(
				"Pct-" + percent,
				null,
				PromotionStrategyType.PERCENTAGE_DISCOUNT,
				new BigDecimal(percent),
				null,
				null,
				null,
				null,
				stackable,
				priority,
				true
		);
	}

	private static PromotionStrategy fixedStrategy(String amount, boolean stackable, int priority) {
		return PromotionStrategy.createNew(
				"Fixed-" + amount,
				null,
				PromotionStrategyType.FIXED_AMOUNT_DISCOUNT,
				null,
				new BigDecimal(amount),
				null,
				null,
				null,
				stackable,
				priority,
				true
		);
	}

	private static PromotionStrategy productPercentStrategy(String percent, boolean stackable, int priority) {
		return PromotionStrategy.createNew(
				"ProductPct",
				null,
				PromotionStrategyType.PRODUCT_DISCOUNT,
				new BigDecimal(percent),
				null,
				null,
				null,
				null,
				stackable,
				priority,
				true
		);
	}

	private static PromotionStrategy categoryFixedStrategy(String amount, boolean stackable, int priority) {
		return PromotionStrategy.createNew(
				"CatFixed",
				null,
				PromotionStrategyType.CATEGORY_DISCOUNT,
				null,
				new BigDecimal(amount),
				null,
				null,
				null,
				stackable,
				priority,
				true
		);
	}

	private static PromotionCampaign activeCampaign(
			PromotionStrategy strategy,
			String name,
			String slug,
			int priority,
			boolean stackable
	) {
		return campaignWithWindow(strategy, name, slug, priority, stackable, WINDOW_START, WINDOW_END);
	}

	private static PromotionCampaign campaignWithWindow(
			PromotionStrategy strategy,
			String name,
			String slug,
			int priority,
			boolean stackable,
			Instant startsAt,
			Instant endsAt
	) {
		PromotionCampaign campaign = PromotionCampaign.createNew(strategy, name, slug, startsAt, endsAt, null);
		campaign.setStatus(PromotionCampaignStatus.ACTIVE);
		campaign.setPriority(priority);
		campaign.setStackable(stackable);
		setId(campaign, UUID.randomUUID());
		setId(strategy, UUID.randomUUID());
		return campaign;
	}

	private static void setId(Object entity, UUID id) {
		try {
			Field field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
