package com.aptech.aptech4.catalog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic cleanup of expired ACTIVE reservations (ADR-006). Lazy expiry also runs on reserve/get.
 */
@Component
public class InventoryReservationExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(InventoryReservationExpiryJob.class);

	private final InventoryReservationService reservationService;

	public InventoryReservationExpiryJob(InventoryReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@Scheduled(fixedDelayString = "${aptech.inventory.expiry-poll-ms:60000}")
	public void expireDue() {
		int released = reservationService.expireDueReservations();
		if (released > 0) {
			log.info("Released {} expired stock reservation(s)", released);
		}
	}
}
