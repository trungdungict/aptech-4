package com.aptech.aptech4.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

	@Id
	private UUID productId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity_on_hand", nullable = false)
	private int quantityOnHand;

	@Column(name = "quantity_reserved", nullable = false)
	private int quantityReserved;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Inventory() {
	}

	public static Inventory forProduct(Product product, int quantityOnHand) {
		Inventory inventory = new Inventory();
		inventory.setProduct(product);
		inventory.quantityOnHand = quantityOnHand;
		inventory.quantityReserved = 0;
		inventory.updatedAt = Instant.now();
		return inventory;
	}

	/** available = on_hand - reserved (never negative when invariants hold). */
	public int getAvailable() {
		return quantityOnHand - quantityReserved;
	}

	public UUID getProductId() {
		return productId;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		// Leave productId null for new rows; @MapsId copies it on persist.
		this.product = product;
		if (product != null) {
			product.setInventory(this);
		}
	}

	public int getQuantityOnHand() {
		return quantityOnHand;
	}

	public void setQuantityOnHand(int quantityOnHand) {
		this.quantityOnHand = quantityOnHand;
	}

	public int getQuantityReserved() {
		return quantityReserved;
	}

	public void setQuantityReserved(int quantityReserved) {
		this.quantityReserved = quantityReserved;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		UUID id = productId;
		return id != null && id.equals(((Inventory) other).productId);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public String toString() {
		return "Inventory{productId=" + productId + ", onHand=" + quantityOnHand
				+ ", reserved=" + quantityReserved + '}';
	}
}
