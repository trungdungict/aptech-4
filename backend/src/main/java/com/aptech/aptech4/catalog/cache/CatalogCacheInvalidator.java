package com.aptech.aptech4.catalog.cache;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Evicts public catalog cache regions after mutations (ADR-015).
 * Product list keys are combinatorial → clear entire list/detail regions on writes.
 */
@Component
public class CatalogCacheInvalidator {

	private static final Logger log = LoggerFactory.getLogger(CatalogCacheInvalidator.class);

	private final CacheManager cacheManager;

	public CatalogCacheInvalidator(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	public void onProductMutation(UUID productId) {
		clear(CatalogCacheNames.PRODUCTS_LIST);
		if (productId != null) {
			evict(CatalogCacheNames.PRODUCTS_DETAIL, CatalogCacheKeys.productDetail(productId));
		}
		else {
			clear(CatalogCacheNames.PRODUCTS_DETAIL);
		}
		log.debug("Catalog product cache invalidated productId={}", productId);
	}

	/** Inventory / soft-hold changes affect inStock filters and product detail. */
	public void onInventoryMutation() {
		clear(CatalogCacheNames.PRODUCTS_LIST);
		clear(CatalogCacheNames.PRODUCTS_DETAIL);
		log.debug("Catalog product caches invalidated after inventory change");
	}

	public void onCategoryMutation(UUID categoryId) {
		clear(CatalogCacheNames.CATEGORIES_LIST);
		if (categoryId != null) {
			evict(CatalogCacheNames.CATEGORIES_DETAIL, CatalogCacheKeys.categoryDetail(categoryId));
		}
		else {
			clear(CatalogCacheNames.CATEGORIES_DETAIL);
		}
		// Public product payloads embed category name; listings filter by active category.
		clear(CatalogCacheNames.PRODUCTS_LIST);
		clear(CatalogCacheNames.PRODUCTS_DETAIL);
		log.debug("Catalog category (+product) caches invalidated categoryId={}", categoryId);
	}

	private void clear(String cacheName) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache != null) {
			cache.clear();
		}
	}

	private void evict(String cacheName, Object key) {
		Cache cache = cacheManager.getCache(cacheName);
		if (cache != null) {
			cache.evict(key);
		}
	}
}
