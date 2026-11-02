package com.aptech.aptech4.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.inventory")
public class InventoryProperties {

	/**
	 * TTL for ACTIVE reservations before they are treated as expired (seconds).
	 * Default 900 (15 minutes) for demo checkout window.
	 */
	private long reservationTtlSeconds = 900;

	/**
	 * Admin inventory low-stock threshold for list/summary filters.
	 * LOW means {@code 0 < available <= threshold} where
	 * {@code available = quantity_on_hand - quantity_reserved} (missing inventory row → available 0).
	 * Independent of {@code aptech.ai.low-stock-threshold}.
	 */
	private int lowStockThreshold = 5;

	public long getReservationTtlSeconds() {
		return reservationTtlSeconds;
	}

	public void setReservationTtlSeconds(long reservationTtlSeconds) {
		this.reservationTtlSeconds = reservationTtlSeconds;
	}

	public int getLowStockThreshold() {
		return lowStockThreshold;
	}

	public void setLowStockThreshold(int lowStockThreshold) {
		if (lowStockThreshold < 0) {
			throw new IllegalArgumentException("aptech.inventory.low-stock-threshold must be >= 0");
		}
		this.lowStockThreshold = lowStockThreshold;
	}
}
