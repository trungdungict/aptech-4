package com.aptech.aptech4.catalog.domain;

import java.time.Instant;
import java.util.Locale;
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

@Entity
@Table(name = "categories")
public class Category extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "slug", nullable = false, unique = true, length = 140)
	private String slug;

	@Column(name = "description")
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Category() {
	}

	public static Category createNew(String name, String slug, String description, Category parent) {
		Category category = new Category();
		category.name = name;
		category.slug = slug;
		category.description = description;
		category.parent = parent;
		category.active = true;
		Instant now = Instant.now();
		category.createdAt = now;
		category.updatedAt = now;
		return category;
	}

	public static String slugify(String name) {
		String slug = name.trim().toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-+|-+$", "");
		if (slug.isBlank()) {
			slug = "category";
		}
		if (slug.length() > 140) {
			slug = slug.substring(0, 140).replaceAll("-+$", "");
		}
		return slug;
	}

	public void touch() {
		this.updatedAt = Instant.now();
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSlug() {
		return slug;
	}

	public void setSlug(String slug) {
		this.slug = slug;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Category getParent() {
		return parent;
	}

	public void setParent(Category parent) {
		this.parent = parent;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "Category{id=" + id + ", slug='" + slug + "', active=" + active + '}';
	}
}
