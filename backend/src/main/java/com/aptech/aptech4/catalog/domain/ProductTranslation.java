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
		name = "product_translations",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_product_translations_product_locale",
				columnNames = { "product_id", "locale" }
		)
)
public class ProductTranslation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "locale", nullable = false, length = 5)
	private String locale;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	protected ProductTranslation() {
	}

	public static ProductTranslation createNew(Product product, String locale, String name, String description) {
		ProductTranslation row = new ProductTranslation();
		row.product = product;
		row.locale = locale;
		row.name = name;
		row.description = description;
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public Product getProduct() {
		return product;
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
