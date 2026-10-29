package com.aptech.aptech4.cart.domain;

import java.time.Instant;
import java.util.UUID;

import com.aptech.aptech4.catalog.domain.Product;
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
		name = "cart_items",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_cart_items_cart_product",
				columnNames = { "cart_id", "product_id" }
		)
)
public class CartItem extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "added_at", nullable = false)
	private Instant addedAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected CartItem() {
	}

	public static CartItem createNew(Cart cart, Product product, int quantity) {
		CartItem item = new CartItem();
		item.cart = cart;
		item.product = product;
		item.quantity = quantity;
		Instant now = Instant.now();
		item.addedAt = now;
		item.updatedAt = now;
		return item;
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

	public Cart getCart() {
		return cart;
	}

	public void setCart(Cart cart) {
		this.cart = cart;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Instant getAddedAt() {
		return addedAt;
	}

	public void setAddedAt(Instant addedAt) {
		this.addedAt = addedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "CartItem{id=" + id + ", quantity=" + quantity + '}';
	}
}
