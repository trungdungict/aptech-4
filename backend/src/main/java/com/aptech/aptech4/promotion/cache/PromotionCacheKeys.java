package com.aptech.aptech4.promotion.cache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.aptech.aptech4.common.locale.AppLocale;
import com.aptech.aptech4.common.locale.RequestLocaleHolder;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

/** Deterministic cache keys for public promotion placement queries (ADR-020 R7). */
public final class PromotionCacheKeys {

	private PromotionCacheKeys() {
	}

	/** Locale-aware homepage key for the current request locale. */
	public static String homepage() {
		return "homepage|" + currentLocaleCode();
	}

	public static String placements(Collection<PromotionPlacementKey> keys) {
		String base;
		if (keys == null || keys.isEmpty()) {
			base = "ALL";
		}
		else {
			base = keys.stream()
					.filter(k -> k != null)
					.sorted(Comparator.comparing(Enum::name))
					.map(Enum::name)
					.collect(Collectors.joining(","));
		}
		return base + "|" + currentLocaleCode();
	}

	public static String placements(PromotionPlacementKey... keys) {
		if (keys == null || keys.length == 0) {
			return placements((Collection<PromotionPlacementKey>) null);
		}
		return placements(Arrays.asList(keys));
	}

	static String currentLocaleCode() {
		AppLocale locale = RequestLocaleHolder.get();
		return locale == null ? AppLocale.DEFAULT.code() : locale.code();
	}
}
