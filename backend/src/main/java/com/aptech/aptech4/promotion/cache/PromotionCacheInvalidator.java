package com.aptech.aptech4.promotion.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Clears public promotion placement caches after Admin mutations (ADR-020 R7).
 * TTL is a fallback only — do not rely on it for correctness after writes.
 */
@Component
public class PromotionCacheInvalidator {

	private static final Logger log = LoggerFactory.getLogger(PromotionCacheInvalidator.class);

	private final CacheManager cacheManager;

	public PromotionCacheInvalidator(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public void onPromotionMutation() {
		clear(PromotionCacheNames.HOMEPAGE);
		clear(PromotionCacheNames.PLACEMENTS);
		log.debug("Promotion placement caches invalidated");
	}

	private void clear(String cacheName) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache != null) {
			cache.clear();
		}
	}
}
