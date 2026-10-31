package com.aptech.aptech4.promotion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic ACTIVE→EXPIRED lifecycle hygiene when {@code now > ends_at} (ADR-020 R9).
 * Runtime pricing/placement effectiveness does not depend on this job.
 */
@Component
public class CampaignExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(CampaignExpiryJob.class);

	private final PromotionCampaignService campaignService;

	public CampaignExpiryJob(PromotionCampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@Scheduled(fixedDelayString = "${aptech.promotions.expiry-poll-ms:60000}")
	public void expireDue() {
		int expired = campaignService.expireDueCampaigns();
		if (expired > 0) {
			log.info("Expired {} promotion campaign(s) past ends_at", expired);
		}
	}
}
