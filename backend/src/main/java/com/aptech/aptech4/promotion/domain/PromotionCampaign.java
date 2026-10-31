package com.aptech.aptech4.promotion.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "promotion_campaigns")
public class PromotionCampaign extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "strategy_id", nullable = false)
	private PromotionStrategy strategy;

	@Column(name = "name", nullable = false, length = 160)
	private String name;

	@Column(name = "slug", nullable = false, unique = true, length = 160)
	private String slug;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PromotionCampaignStatus status = PromotionCampaignStatus.DRAFT;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	/** Runtime priority (copied from strategy on create; independent thereafter). */
	@Column(name = "priority", nullable = false)
	private int priority;

	/** Runtime stackable flag (copied from strategy on create; independent thereafter). */
	@Column(name = "stackable", nullable = false)
	private boolean stackable;

	@Column(name = "notes")
	private String notes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<PromotionCampaignProduct> products = new HashSet<>();

	@OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<PromotionCampaignCategory> categories = new HashSet<>();

	@OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<PromotionPlacement> placements = new ArrayList<>();

	protected PromotionCampaign() {
	}

	/**
	 * Creates a DRAFT campaign, copying strategy default priority/stackable (AC-E4).
	 */
	public static PromotionCampaign createNew(
			PromotionStrategy strategy,
			String name,
			String slug,
			Instant startsAt,
			Instant endsAt,
			String notes
	) {
		if (strategy == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "strategyId is required");
		}
		requireWindow(startsAt, endsAt);
		PromotionCampaign campaign = new PromotionCampaign();
		campaign.strategy = strategy;
		campaign.name = name;
		campaign.slug = slugify(slug == null || slug.isBlank() ? name : slug);
		campaign.status = PromotionCampaignStatus.DRAFT;
		campaign.startsAt = startsAt;
		campaign.endsAt = endsAt;
		campaign.priority = strategy.getPriority();
		campaign.stackable = strategy.isStackable();
		campaign.notes = notes;
		Instant now = Instant.now();
		campaign.createdAt = now;
		campaign.updatedAt = now;
		return campaign;
	}

	public static String slugify(String raw) {
		String slug = raw.trim().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+|-+$", "");
		if (slug.isBlank()) {
			slug = "campaign";
		}
		if (slug.length() > 160) {
			slug = slug.substring(0, 160).replaceAll("-+$", "");
		}
		return slug;
	}

	public static void requireWindow(Instant startsAt, Instant endsAt) {
		if (startsAt == null || endsAt == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "startsAt and endsAt are required");
		}
		if (!endsAt.isAfter(startsAt)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "endsAt must be after startsAt");
		}
	}

	/** Inclusive UTC window: startsAt &lt;= now &lt;= endsAt. */
	public boolean isWithinWindow(Instant now) {
		return now != null && !now.isBefore(startsAt) && !now.isAfter(endsAt);
	}

	public boolean isPricingEffective(Instant now) {
		return status == PromotionCampaignStatus.ACTIVE
				&& isWithinWindow(now)
				&& strategy != null
				&& strategy.isActive()
				&& strategy.getType() != PromotionStrategyType.BANNER_ONLY;
	}

	public boolean isPlacementEffective(Instant now) {
		return status == PromotionCampaignStatus.ACTIVE
				&& isWithinWindow(now)
				&& strategy != null
				&& strategy.isActive();
	}

	public void setStrategy(PromotionStrategy strategy) {
		if (strategy == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "strategyId is required");
		}
		this.strategy = strategy;
		this.updatedAt = Instant.now();
	}

	public void setSchedule(Instant startsAt, Instant endsAt) {
		requireWindow(startsAt, endsAt);
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.updatedAt = Instant.now();
	}

	public void setStatus(PromotionCampaignStatus status) {
		this.status = status;
		this.updatedAt = Instant.now();
	}

	public void setPriority(int priority) {
		this.priority = priority;
		this.updatedAt = Instant.now();
	}

	public void setStackable(boolean stackable) {
		this.stackable = stackable;
		this.updatedAt = Instant.now();
	}

	public void setName(String name) {
		this.name = name;
		this.updatedAt = Instant.now();
	}

	public void setSlug(String slug) {
		this.slug = slugify(slug);
		this.updatedAt = Instant.now();
	}

	public void setNotes(String notes) {
		this.notes = notes;
		this.updatedAt = Instant.now();
	}

	/**
	 * Display/runtime status: manual status gated by inclusive UTC window for ACTIVE rows.
	 */
	public PromotionCampaignStatus resolveEffectiveStatus(Instant now) {
		if (status == PromotionCampaignStatus.DRAFT
				|| status == PromotionCampaignStatus.PAUSED
				|| status == PromotionCampaignStatus.EXPIRED
				|| status == PromotionCampaignStatus.SCHEDULED) {
			return status;
		}
		// ACTIVE
		if (now.isBefore(startsAt)) {
			return PromotionCampaignStatus.SCHEDULED;
		}
		if (now.isAfter(endsAt)) {
			return PromotionCampaignStatus.EXPIRED;
		}
		return PromotionCampaignStatus.ACTIVE;
	}

	public void replaceProductIds(Set<UUID> productIds) {
		products.clear();
		if (productIds != null) {
			for (UUID productId : productIds) {
				products.add(PromotionCampaignProduct.link(this, productId));
			}
		}
		this.updatedAt = Instant.now();
	}

	public void replaceCategoryIds(Set<UUID> categoryIds) {
		categories.clear();
		if (categoryIds != null) {
			for (UUID categoryId : categoryIds) {
				categories.add(PromotionCampaignCategory.link(this, categoryId));
			}
		}
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public PromotionStrategy getStrategy() {
		return strategy;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public PromotionCampaignStatus getStatus() {
		return status;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public int getPriority() {
		return priority;
	}

	public boolean isStackable() {
		return stackable;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Set<PromotionCampaignProduct> getProducts() {
		return products;
	}

	public Set<PromotionCampaignCategory> getCategories() {
		return categories;
	}

	public List<PromotionPlacement> getPlacements() {
		return placements;
	}
}
