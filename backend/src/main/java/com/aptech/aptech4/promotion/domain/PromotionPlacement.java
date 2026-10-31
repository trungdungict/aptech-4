package com.aptech.aptech4.promotion.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.aptech.aptech4.catalog.upload.ImageUploadPathRules;
import com.aptech.aptech4.catalog.upload.UploadPurpose;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_placements")
public class PromotionPlacement extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "campaign_id", nullable = false)
	private PromotionCampaign campaign;

	@Enumerated(EnumType.STRING)
	@Column(name = "placement_key", nullable = false, length = 40)
	private PromotionPlacementKey placementKey;

	@Column(name = "title", nullable = false, length = 160)
	private String title;

	@Column(name = "subtitle", length = 255)
	private String subtitle;

	@Column(name = "cta_label", length = 80)
	private String ctaLabel;

	@Enumerated(EnumType.STRING)
	@Column(name = "cta_type", nullable = false, length = 20)
	private PromotionCtaType ctaType = PromotionCtaType.NONE;

	@Column(name = "cta_product_id")
	private UUID ctaProductId;

	@Column(name = "cta_category_id")
	private UUID ctaCategoryId;

	@Column(name = "cta_collection_key", length = 80)
	private String ctaCollectionKey;

	@Column(name = "cta_url", length = 512)
	private String ctaUrl;

	@Column(name = "desktop_image_url", nullable = false, length = 512)
	private String desktopImageUrl;

	@Column(name = "mobile_image_url", length = 512)
	private String mobileImageUrl;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "ends_at")
	private Instant endsAt;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected PromotionPlacement() {
	}

	public static PromotionPlacement createNew(
			PromotionCampaign campaign,
			PromotionPlacementKey placementKey,
			String title,
			String subtitle,
			String ctaLabel,
			PromotionCtaType ctaType,
			UUID ctaProductId,
			UUID ctaCategoryId,
			String ctaCollectionKey,
			String ctaUrl,
			String desktopImageUrl,
			String mobileImageUrl,
			int displayOrder,
			Instant startsAt,
			Instant endsAt,
			boolean enabled
	) {
		if (campaign == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "campaign is required");
		}
		PromotionPlacement placement = new PromotionPlacement();
		placement.campaign = campaign;
		placement.applyContent(
				placementKey, title, subtitle, ctaLabel, ctaType,
				ctaProductId, ctaCategoryId, ctaCollectionKey, ctaUrl,
				desktopImageUrl, mobileImageUrl, displayOrder, startsAt, endsAt, enabled
		);
		Instant now = Instant.now();
		placement.createdAt = now;
		placement.updatedAt = now;
		campaign.getPlacements().add(placement);
		return placement;
	}

	public void applyContent(
			PromotionPlacementKey placementKey,
			String title,
			String subtitle,
			String ctaLabel,
			PromotionCtaType ctaType,
			UUID ctaProductId,
			UUID ctaCategoryId,
			String ctaCollectionKey,
			String ctaUrl,
			String desktopImageUrl,
			String mobileImageUrl,
			int displayOrder,
			Instant startsAt,
			Instant endsAt,
			boolean enabled
	) {
		if (placementKey == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "placementKey is required");
		}
		if (title == null || title.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "title is required");
		}
		if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "placement endsAt must be after startsAt");
		}
		String desktop = ImageUploadPathRules.normalizeOrNull(UploadPurpose.PROMOTION, desktopImageUrl);
		if (desktop == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "desktopImageUrl is required");
		}
		this.placementKey = placementKey;
		this.title = title.trim();
		this.subtitle = subtitle;
		this.ctaLabel = ctaLabel;
		this.ctaType = ctaType == null ? PromotionCtaType.NONE : ctaType;
		this.ctaProductId = ctaProductId;
		this.ctaCategoryId = ctaCategoryId;
		this.ctaCollectionKey = ctaCollectionKey;
		this.ctaUrl = ctaUrl;
		this.desktopImageUrl = desktop;
		this.mobileImageUrl = ImageUploadPathRules.normalizeOrNull(UploadPurpose.PROMOTION, mobileImageUrl);
		this.displayOrder = displayOrder;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.enabled = enabled;
		this.updatedAt = Instant.now();
	}

	/** Placement-level window (optional) inside an already placement-effective campaign. */
	public boolean isVisibleAt(Instant now) {
		if (!enabled) {
			return false;
		}
		if (startsAt != null && now.isBefore(startsAt)) {
			return false;
		}
		if (endsAt != null && now.isAfter(endsAt)) {
			return false;
		}
		return true;
	}

	public UUID getId() {
		return id;
	}

	public PromotionCampaign getCampaign() {
		return campaign;
	}

	public PromotionPlacementKey getPlacementKey() {
		return placementKey;
	}

	public String getTitle() {
		return title;
	}

	public String getSubtitle() {
		return subtitle;
	}

	public String getCtaLabel() {
		return ctaLabel;
	}

	public PromotionCtaType getCtaType() {
		return ctaType;
	}

	public UUID getCtaProductId() {
		return ctaProductId;
	}

	public UUID getCtaCategoryId() {
		return ctaCategoryId;
	}

	public String getCtaCollectionKey() {
		return ctaCollectionKey;
	}

	public String getCtaUrl() {
		return ctaUrl;
	}

	public String getDesktopImageUrl() {
		return desktopImageUrl;
	}

	public String getMobileImageUrl() {
		return mobileImageUrl;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
