package com.aptech.aptech4.promotion.domain;

import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "promotion_campaign_translations",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_promo_campaign_translations_campaign_locale",
				columnNames = { "campaign_id", "locale" }
		)
)
public class PromotionCampaignTranslation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private PromotionCampaign campaign;

	@Column(name = "locale", nullable = false, length = 5)
	private String locale;

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	protected PromotionCampaignTranslation() {
	}

	public static PromotionCampaignTranslation createNew(PromotionCampaign campaign, String locale, String name) {
		PromotionCampaignTranslation row = new PromotionCampaignTranslation();
		row.campaign = campaign;
		row.locale = locale;
		row.name = name;
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public PromotionCampaign getCampaign() {
		return campaign;
	}

	public String getLocale() {
		return locale;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
