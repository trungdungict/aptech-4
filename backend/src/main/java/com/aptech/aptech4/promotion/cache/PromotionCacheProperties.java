package com.aptech.aptech4.promotion.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.cache.promotions")
public class PromotionCacheProperties {

	/** Short TTL safety net; Admin mutations must invalidate (R7). */
	private long homepageTtlSeconds = 30;

	private long placementsTtlSeconds = 30;

	public long getHomepageTtlSeconds() {
		return homepageTtlSeconds;
	}

	public void setHomepageTtlSeconds(long homepageTtlSeconds) {
		this.homepageTtlSeconds = homepageTtlSeconds;
	}

	public long getPlacementsTtlSeconds() {
		return placementsTtlSeconds;
	}

	public void setPlacementsTtlSeconds(long placementsTtlSeconds) {
		this.placementsTtlSeconds = placementsTtlSeconds;
	}
}
