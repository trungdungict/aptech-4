package com.aptech.aptech4.promotion.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CampaignPreviewRequest(Instant at) {
}
