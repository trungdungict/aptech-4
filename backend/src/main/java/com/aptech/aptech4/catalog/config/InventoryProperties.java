package com.aptech.aptech4.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aptech.inventory")
public class InventoryProperties {

	/**
	 * TTL for ACTIVE reservations before they are treated as expired (seconds).
	 * Default 900 (15 minutes) for demo checkout window.
	 */
	private long reservationTtlSeconds = 900;

	public long getReservationTtlSeconds() {
		return reservationTtlSeconds;
	}

	public void setReservationTtlSeconds(long reservationTtlSeconds) {
		this.reservationTtlSeconds = reservationTtlSeconds;
	}
}
