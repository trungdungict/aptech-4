package com.aptech.aptech4.seed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Natural keys for grocery promotion fixtures (ADR-010). Codes/slugs are exact-match owned keys —
 * never a loose prefix heuristic for deletes. Visible names are production-like Vietnamese grocery copy.
 */
public final class DemoPromotionCatalog {

	/**
	 * Deterministic public path + on-disk seed banner (UUID-shaped filename per upload rules).
	 * Artwork is copied from classpath {@code db/demo/promo-hero.jpg} (FoodMart slide), not a 1×1 placeholder.
	 */
	public static final String HERO_IMAGE_PUBLIC_PATH =
			"/uploads/promotions/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg";
	public static final String HERO_IMAGE_FILENAME = "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg";
	public static final String HERO_IMAGE_CLASSPATH = "db/demo/promo-hero.jpg";

	public static final String HERO_TITLE = "Ưu đãi mua sắm tiết kiệm";
	public static final String HERO_SUBTITLE = "Chọn thực phẩm tươi mỗi ngày — ưu đãi áp dụng khi thanh toán";
	public static final String HERO_TITLE_EN = "Save on everyday groceries";
	public static final String HERO_SUBTITLE_EN = "Pick fresh food daily — discounts apply at checkout";
	public static final String HERO_CTA_VI = "Xem danh mục";
	public static final String HERO_CTA_EN = "Browse catalog";

	/** FoodMart SKUs / slugs used for eligibility fixtures. */
	public static final String PRODUCT_SKU_APPLE = "FOOD-FRUIT-APPLE-001";
	public static final String PRODUCT_SKU_BANANA = "FOOD-FRUIT-BANANA-001";
	public static final String CATEGORY_SLUG_BEVERAGES = "food-beverages";
	public static final String CATEGORY_SLUG_FRUITS = "food-fruits-vegetables";

	/** Display name pairs: VI (canonical base) + EN translation. */
	public record DisplayName(String vi, String en) {
	}

	public static DisplayName strategyName(StrategyCode code) {
		return switch (code) {
			case PCT_ORDER -> new DisplayName("Ưu đãi thành viên — giảm 10%", "Member offer — 10% off");
			case FIXED_ORDER -> new DisplayName("Ưu đãi đơn hàng — giảm $0.60", "Order offer — $0.60 off");
			case PRODUCT -> new DisplayName("Ưu đãi táo tươi — giảm 15%", "Fresh apple offer — 15% off");
			case CATEGORY -> new DisplayName("Giảm 8% nhóm đồ uống", "8% off beverages");
			case ORDER_OR -> new DisplayName("Ưu đãi linh hoạt — giảm 5%", "Flexible offer — 5% off");
			case STACK_A -> new DisplayName("Ưu đãi kết hợp — giảm 5%", "Stackable offer — 5% off");
			case STACK_B -> new DisplayName("Ưu đãi kết hợp — giảm $0.20", "Stackable offer — $0.20 off");
			case BANNER -> new DisplayName("Banner trang chủ", "Homepage banner");
			case LIFECYCLE -> new DisplayName("Chiến lược vòng đời cơ bản", "Basic lifecycle strategy");
		};
	}

	public static DisplayName campaignName(CampaignSlug slug) {
		return switch (slug) {
			case PCT_ACTIVE -> new DisplayName("Ưu đãi thành viên — giảm 10%", "Member offer — 10% off");
			case FIXED_ACTIVE -> new DisplayName("Ưu đãi đơn hàng — giảm $0.60", "Order offer — $0.60 off");
			case PRODUCT_APPLE -> new DisplayName("Ưu đãi táo tươi — giảm 15%", "Fresh apple offer — 15% off");
			case CATEGORY_BEVERAGES -> new DisplayName("Giảm 8% nhóm đồ uống", "8% off beverages");
			case ORDER_OR -> new DisplayName("Ưu đãi linh hoạt — giảm 5%", "Flexible offer — 5% off");
			case STACK_A -> new DisplayName("Ưu đãi kết hợp — giảm 5%", "Stackable offer — 5% off");
			case STACK_B -> new DisplayName("Ưu đãi kết hợp — giảm $0.20", "Stackable offer — $0.20 off");
			case HERO -> new DisplayName(HERO_TITLE, HERO_TITLE_EN);
			case SCHEDULED -> new DisplayName("Ưu đãi sắp diễn ra", "Upcoming offer");
			case PAUSED -> new DisplayName("Ưu đãi tạm dừng", "Paused offer");
			case DRAFT -> new DisplayName("Ưu đãi nháp", "Draft offer");
			case EXPIRED -> new DisplayName("Ưu đãi hết hạn", "Expired offer");
		};
	}

	/**
	 * Former Phase-8 codes (Demo/Smoke era). Purged on seed/reset so local DBs migrate without volume wipe.
	 */
	private static final List<String> RETIRED_STRATEGY_CODES = List.of(
			"DEMO-PROMO-PCT-ORDER",
			"DEMO-PROMO-FIXED-ORDER",
			"DEMO-PROMO-PRODUCT",
			"DEMO-PROMO-CATEGORY",
			"DEMO-PROMO-ORDER-OR",
			"DEMO-PROMO-STACK-A",
			"DEMO-PROMO-STACK-B",
			"DEMO-PROMO-BANNER",
			"DEMO-PROMO-LIFECYCLE"
	);

	private static final List<String> RETIRED_CAMPAIGN_SLUGS = List.of(
			"demo-promo-pct-active",
			"demo-promo-fixed-active",
			"demo-promo-product-apple",
			"demo-promo-category-beverages",
			"demo-promo-order-or",
			"demo-promo-stack-a",
			"demo-promo-stack-b",
			"demo-promo-hero",
			"demo-promo-scheduled",
			"demo-promo-paused",
			"demo-promo-draft",
			"demo-promo-expired"
	);

	public enum StrategyCode {
		PCT_ORDER("PROMO-MEMBER-PCT-10"),
		FIXED_ORDER("PROMO-ORDER-FIXED-15K"),
		PRODUCT("PROMO-APPLE-PCT-15"),
		CATEGORY("PROMO-BEV-PCT-8"),
		ORDER_OR("PROMO-MIX-OR-5"),
		STACK_A("PROMO-STACK-PCT-5"),
		STACK_B("PROMO-STACK-FIXED-5K"),
		BANNER("PROMO-HOME-BANNER"),
		LIFECYCLE("PROMO-LIFECYCLE-BASE");

		private final String code;

		StrategyCode(String code) {
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	public enum CampaignSlug {
		PCT_ACTIVE("uu-dai-thanh-vien-10"),
		FIXED_ACTIVE("uu-dai-don-hang-15k"),
		PRODUCT_APPLE("uu-dai-tao-tuoi-15"),
		CATEGORY_BEVERAGES("giam-8-do-uong"),
		ORDER_OR("uu-dai-mix-or-5"),
		STACK_A("uu-dai-stack-pct-5"),
		STACK_B("uu-dai-stack-fixed-5k"),
		HERO("banner-trang-chu"),
		SCHEDULED("uu-dai-sap-dien-ra"),
		PAUSED("uu-dai-tam-dung"),
		DRAFT("uu-dai-nhap"),
		EXPIRED("uu-dai-het-han");

		private final String slug;

		CampaignSlug(String slug) {
			this.slug = slug;
		}

		public String slug() {
			return slug;
		}
	}

	private DemoPromotionCatalog() {
	}

	public static List<String> strategyCodes() {
		return java.util.Arrays.stream(StrategyCode.values()).map(StrategyCode::code).toList();
	}

	public static List<String> campaignSlugs() {
		return java.util.Arrays.stream(CampaignSlug.values()).map(CampaignSlug::slug).toList();
	}

	public static List<String> retiredStrategyCodes() {
		return RETIRED_STRATEGY_CODES;
	}

	public static List<String> retiredCampaignSlugs() {
		return RETIRED_CAMPAIGN_SLUGS;
	}

	/** Active + retired keys (reset / purge scope). */
	public static List<String> resetStrategyCodes() {
		return mergeUnique(strategyCodes(), retiredStrategyCodes());
	}

	public static List<String> resetCampaignSlugs() {
		return mergeUnique(campaignSlugs(), retiredCampaignSlugs());
	}

	private static List<String> mergeUnique(List<String> primary, List<String> secondary) {
		Set<String> merged = new LinkedHashSet<>(primary);
		merged.addAll(secondary);
		return List.copyOf(new ArrayList<>(merged));
	}
}
