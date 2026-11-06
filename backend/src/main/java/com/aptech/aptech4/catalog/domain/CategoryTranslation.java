package com.aptech.aptech4.catalog.domain;

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
		name = "category_translations",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_category_translations_category_locale",
				columnNames = { "category_id", "locale" }
		)
)
public class CategoryTranslation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "locale", nullable = false, length = 5)
	private String locale;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "description")
	private String description;

	protected CategoryTranslation() {
	}

	public static CategoryTranslation createNew(Category category, String locale, String name, String description) {
		CategoryTranslation row = new CategoryTranslation();
		row.category = category;
		row.locale = locale;
		row.name = name;
		row.description = description;
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public Category getCategory() {
		return category;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
