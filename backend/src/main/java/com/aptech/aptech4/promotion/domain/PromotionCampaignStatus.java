package com.aptech.aptech4.promotion.domain;

/** Manual campaign lifecycle status (ADR-020). Effectiveness also requires the time window. */
public enum PromotionCampaignStatus {
	DRAFT,
	SCHEDULED,
	ACTIVE,
	PAUSED,
	EXPIRED
}
