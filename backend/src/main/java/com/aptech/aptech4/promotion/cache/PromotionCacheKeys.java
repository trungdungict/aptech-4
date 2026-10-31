package com.aptech.aptech4.promotion.cache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

/** Deterministic cache keys for public promotion placement queries (ADR-020 R7). */
public final class PromotionCacheKeys {

	public static final String HOMEPAGE = "homepage";

	private PromotionCacheKeys() {
	}

	public static String placements(Collection<PromotionPlacementKey> keys) {
		if (keys == null || keys.isEmpty()) {
			return "ALL";
		}
		return keys.stream()
				.filter(k -> k != null)
				.sorted(Comparator.comparing(Enum::name))
				.map(Enum::name)
				.collect(Collectors.joining(","));
	}

	public static String placements(PromotionPlacementKey... keys) {
		if (keys == null || keys.length == 0) {
			return "ALL";
		}
		return placements(Arrays.asList(keys));
	}
}
