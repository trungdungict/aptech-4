package com.aptech.aptech4.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private Category category;

	@Column(name = "sku", nullable = false, unique = true, length = 64)
	private String sku;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "VND";

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToOne(mappedBy = "product", fetch = FetchType.LAZY)
	private Inventory inventory;

	protected Product() {
	}

	public static Product createNew(
			Category category,
			String sku,
			String name,
			String description,
			BigDecimal unitPrice,
			String currency,
			boolean active
	) {
		Product product = new Product();
		product.category = category;
		product.sku = sku;
		product.name = name;
		product.description = description;
		product.unitPrice = unitPrice;
		product.currency = currency == null || currency.isBlank() ? "VND" : currency;
		product.active = active;
		Instant now = Instant.now();
		product.createdAt = now;
		product.updatedAt = now;
		return product;
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

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
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

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
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

	public Inventory getInventory() {
		return inventory;
	}

	public void setInventory(Inventory inventory) {
		this.inventory = inventory;
	}

	@Override
	public String toString() {
		return "Product{id=" + id + ", sku='" + sku + "'}";
	}
}
