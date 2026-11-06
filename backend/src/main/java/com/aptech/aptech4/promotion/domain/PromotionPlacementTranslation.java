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
		name = "promotion_placement_translations",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_promo_placement_translations_placement_locale",
				columnNames = { "placement_id", "locale" }
		)
)
public class PromotionPlacementTranslation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "placement_id", nullable = false)
	private PromotionPlacement placement;

	@Column(name = "locale", nullable = false, length = 5)
	private String locale;

	@Column(name = "title", nullable = false, length = 160)
	private String title;

	@Column(name = "subtitle", length = 255)
	private String subtitle;

	@Column(name = "cta_label", length = 80)
	private String ctaLabel;

	protected PromotionPlacementTranslation() {
	}

	public static PromotionPlacementTranslation createNew(
			PromotionPlacement placement,
			String locale,
			String title,
			String subtitle,
			String ctaLabel
	) {
		PromotionPlacementTranslation row = new PromotionPlacementTranslation();
		row.placement = placement;
		row.locale = locale;
		row.title = title;
		row.subtitle = subtitle;
		row.ctaLabel = ctaLabel;
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public PromotionPlacement getPlacement() {
		return placement;
	}

	public String getLocale() {
		return locale;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	public String getCtaLabel() {
		return ctaLabel;
	}

	public void setCtaLabel(String ctaLabel) {
		this.ctaLabel = ctaLabel;
	}
}
