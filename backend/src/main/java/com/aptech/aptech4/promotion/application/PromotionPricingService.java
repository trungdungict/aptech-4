package com.aptech.aptech4.promotion.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aptech.aptech4.promotion.domain.PromotionCampaign;
import com.aptech.aptech4.promotion.domain.PromotionCampaignCategory;
import com.aptech.aptech4.promotion.domain.PromotionCampaignProduct;
import com.aptech.aptech4.promotion.domain.PromotionStrategy;
import com.aptech.aptech4.promotion.domain.PromotionStrategyType;

/**
 * Backend-only checkout promotion pricing (ADR-020 D2). Does not persist orders/snapshots.
 */
@Service
public class PromotionPricingService {

	private static final int MONEY_SCALE = 2;
	private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
	private static final int SUMMARY_MAX = 255;

	private static final Comparator<PromotionCampaign> PRICING_ORDER = Comparator
			.comparingInt(PromotionCampaign::getPriority).reversed()
			.thenComparing(PromotionCampaign::getStartsAt)
			.thenComparing(PromotionCampaign::getId, Comparator.nullsLast(Comparator.naturalOrder()));

	private final Clock clock;

	public PromotionPricingService(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * Prices the cart against the given campaigns using {@link Clock#instant()} (UTC).
	 * Campaign selection is entirely backend-owned; callers must not filter by client preference.
	 */
	public PromotionPricingResult price(List<PricingCartLine> lines, List<PromotionCampaign> campaigns) {
		List<PricingCartLine> cart = lines == null ? List.of() : List.copyOf(lines);
		BigDecimal subtotal = cartSubtotal(cart);
		if (cart.isEmpty() || subtotal.signum() <= 0) {
			return PromotionPricingResult.none(subtotal);
		}

		Instant now = clock.instant();
		int cartQuantity = cartQuantity(cart);

		List<PromotionCampaign> sorted = (campaigns == null ? List.<PromotionCampaign>of() : campaigns).stream()
				.filter(Objects::nonNull)
				.filter(c -> c.isPricingEffective(now))
				.filter(c -> passesCartMinimums(c.getStrategy(), subtotal, cartQuantity))
				.sorted(PRICING_ORDER)
				.toList();

		List<AppliedPromotion> applied = new ArrayList<>();

		for (PromotionCampaign campaign : sorted) {
			if (campaign.isStackable()) {
				continue;
			}
			AppliedPromotion hit = tryApply(campaign, cart);
			if (hit != null) {
				applied.add(hit);
				break;
			}
		}

		for (PromotionCampaign campaign : sorted) {
			if (!campaign.isStackable()) {
				continue;
			}
			AppliedPromotion hit = tryApply(campaign, cart);
			if (hit != null) {
				applied.add(hit);
			}
		}

		capToSubtotal(applied, subtotal);

		BigDecimal discount = applied.stream()
				.map(AppliedPromotion::discountAmount)
				.reduce(ZERO, BigDecimal::add)
				.setScale(MONEY_SCALE, MONEY_ROUNDING);

		if (discount.compareTo(subtotal) > 0) {
			discount = subtotal;
		}
		if (discount.signum() < 0) {
			discount = ZERO;
		}

		return new PromotionPricingResult(subtotal, discount, buildSummary(applied), List.copyOf(applied));
	}

	private AppliedPromotion tryApply(PromotionCampaign campaign, List<PricingCartLine> cart) {
		BigDecimal eligibleBase = eligibleBase(campaign, cart);
		if (eligibleBase.signum() <= 0) {
			return null;
		}
		BigDecimal campaignDiscount = computeCampaignDiscount(campaign.getStrategy(), eligibleBase);
		if (campaignDiscount.signum() <= 0) {
			return null;
		}
		PromotionStrategy strategy = campaign.getStrategy();
		return new AppliedPromotion(
				campaign.getId(),
				strategy.getId(),
				campaign.getName(),
				campaign.getSlug(),
				strategy.getName(),
				strategy.getType().name(),
				strategy.getCode(),
				campaign.getPriority(),
				campaign.isStackable(),
				strategy.getDiscountPercent(),
				strategy.getDiscountAmount(),
				strategy.getMaxDiscountAmount(),
				eligibleBase,
				campaignDiscount
		);
	}

	private static BigDecimal computeCampaignDiscount(PromotionStrategy strategy, BigDecimal eligibleBase) {
		BigDecimal raw;
		if (strategy.getDiscountPercent() != null) {
			raw = eligibleBase
					.multiply(strategy.getDiscountPercent())
					.divide(new BigDecimal("100"), MONEY_SCALE, MONEY_ROUNDING);
		}
		else if (strategy.getDiscountAmount() != null) {
			raw = strategy.getDiscountAmount().min(eligibleBase).setScale(MONEY_SCALE, MONEY_ROUNDING);
		}
		else {
			return ZERO;
		}
		if (strategy.getMaxDiscountAmount() != null) {
			raw = raw.min(strategy.getMaxDiscountAmount()).setScale(MONEY_SCALE, MONEY_ROUNDING);
		}
		return raw.signum() <= 0 ? ZERO : raw;
	}

	/**
	 * Eligible base per ADR-020 D2 + P22 clarifications.
	 * <ul>
	 *   <li>{@code PRODUCT_DISCOUNT}: matching product ids only</li>
	 *   <li>{@code CATEGORY_DISCOUNT}: matching category ids only</li>
	 *   <li>Order-level % / fixed: empty both → all lines; one set → that set; both → product OR category</li>
	 * </ul>
	 */
	static BigDecimal eligibleBase(PromotionCampaign campaign, List<PricingCartLine> cart) {
		PromotionStrategyType type = campaign.getStrategy().getType();
		Set<UUID> productIds = campaign.getProducts().stream()
				.map(PromotionCampaignProduct::getProductId)
				.collect(Collectors.toSet());
		Set<UUID> categoryIds = campaign.getCategories().stream()
				.map(PromotionCampaignCategory::getCategoryId)
				.collect(Collectors.toSet());

		BigDecimal sum = ZERO;
		for (PricingCartLine line : cart) {
			if (lineMatches(type, productIds, categoryIds, line)) {
				sum = sum.add(scaleMoney(line.lineTotal()));
			}
		}
		return sum.setScale(MONEY_SCALE, MONEY_ROUNDING);
	}

	private static boolean lineMatches(
			PromotionStrategyType type,
			Set<UUID> productIds,
			Set<UUID> categoryIds,
			PricingCartLine line
	) {
		return switch (type) {
			case PRODUCT_DISCOUNT -> productIds.contains(line.productId());
			case CATEGORY_DISCOUNT -> line.categoryId() != null && categoryIds.contains(line.categoryId());
			case PERCENTAGE_DISCOUNT, FIXED_AMOUNT_DISCOUNT -> {
				if (productIds.isEmpty() && categoryIds.isEmpty()) {
					yield true;
				}
				boolean productHit = !productIds.isEmpty() && productIds.contains(line.productId());
				boolean categoryHit = !categoryIds.isEmpty()
						&& line.categoryId() != null
						&& categoryIds.contains(line.categoryId());
				yield productHit || categoryHit;
			}
			case BANNER_ONLY -> false;
		};
	}

	private static boolean passesCartMinimums(PromotionStrategy strategy, BigDecimal cartSubtotal, int cartQuantity) {
		if (strategy.getMinOrderAmount() != null && cartSubtotal.compareTo(strategy.getMinOrderAmount()) < 0) {
			return false;
		}
		if (strategy.getMinQuantity() != null && cartQuantity < strategy.getMinQuantity()) {
			return false;
		}
		return true;
	}

	/**
	 * P22 end-trim: after ADR D2 per-campaign math, cap aggregate at subtotal by trimming from the
	 * end of the applied list. Persist only final amounts; drop zero leftovers so snapshots sum
	 * exactly to {@code order.discount_amount}.
	 */
	private static void capToSubtotal(List<AppliedPromotion> applied, BigDecimal subtotal) {
		BigDecimal sum = applied.stream()
				.map(AppliedPromotion::discountAmount)
				.reduce(ZERO, BigDecimal::add);
		if (sum.compareTo(subtotal) <= 0) {
			return;
		}
		BigDecimal overflow = sum.subtract(subtotal);
		for (int i = applied.size() - 1; i >= 0 && overflow.signum() > 0; i--) {
			AppliedPromotion current = applied.get(i);
			BigDecimal amount = current.discountAmount();
			if (amount.compareTo(overflow) <= 0) {
				overflow = overflow.subtract(amount);
				applied.remove(i);
			}
			else {
				BigDecimal reduced = amount.subtract(overflow).setScale(MONEY_SCALE, MONEY_ROUNDING);
				if (reduced.signum() <= 0) {
					applied.remove(i);
				}
				else {
					applied.set(i, withDiscountAmount(current, reduced));
				}
				overflow = ZERO;
			}
		}
		applied.removeIf(a -> a.discountAmount().signum() <= 0);
	}

	private static AppliedPromotion withDiscountAmount(AppliedPromotion source, BigDecimal discountAmount) {
		return new AppliedPromotion(
				source.campaignId(),
				source.strategyId(),
				source.campaignName(),
				source.campaignSlug(),
				source.strategyName(),
				source.strategyType(),
				source.strategyCode(),
				source.priority(),
				source.stackable(),
				source.discountPercent(),
				source.discountAmountRule(),
				source.maxDiscountAmount(),
				source.eligibleSubtotal(),
				discountAmount
		);
	}

	private static String buildSummary(List<AppliedPromotion> applied) {
		if (applied.isEmpty()) {
			return null;
		}
		String joined = applied.stream()
				.map(AppliedPromotion::campaignName)
				.collect(Collectors.joining(", "));
		if (joined.length() <= SUMMARY_MAX) {
			return joined;
		}
		return joined.substring(0, SUMMARY_MAX);
	}

	private static BigDecimal cartSubtotal(List<PricingCartLine> cart) {
		BigDecimal sum = ZERO;
		for (PricingCartLine line : cart) {
			if (line == null || line.lineTotal() == null) {
				continue;
			}
			sum = sum.add(scaleMoney(line.lineTotal()));
		}
		return sum.setScale(MONEY_SCALE, MONEY_ROUNDING);
	}

	private static int cartQuantity(List<PricingCartLine> cart) {
		int qty = 0;
		for (PricingCartLine line : cart) {
			if (line != null && line.quantity() > 0) {
				qty += line.quantity();
			}
		}
		return qty;
	}

	private static BigDecimal scaleMoney(BigDecimal value) {
		if (value == null) {
			return ZERO;
		}
		return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
	}
}
