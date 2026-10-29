package com.aptech.aptech4.catalog.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.cache.catalog")
public class CatalogCacheProperties {

	/**
	 * TTL for active category list/detail caches.
	 */
	private long categoriesTtlSeconds = 120;

	/**
	 * TTL for public product list/detail caches (shorter: inStock can change via reservations).
	 */
	private long productsTtlSeconds = 60;

	public long getCategoriesTtlSeconds() {
		return categoriesTtlSeconds;
	}

	public void setCategoriesTtlSeconds(long categoriesTtlSeconds) {
		this.categoriesTtlSeconds = categoriesTtlSeconds;
	}

	public long getProductsTtlSeconds() {
		return productsTtlSeconds;
	}

	public void setProductsTtlSeconds(long productsTtlSeconds) {
		this.productsTtlSeconds = productsTtlSeconds;
	}
}
