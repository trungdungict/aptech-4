package com.aptech.aptech4.cart.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.aptech.aptech4.identity.domain.UserAccount;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "carts")
public class Cart extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private UserAccount user;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<CartItem> items = new ArrayList<>();

	protected Cart() {
	}

	public static Cart createFor(UserAccount user) {
		Cart cart = new Cart();
		cart.user = user;
		Instant now = Instant.now();
		cart.createdAt = now;
		cart.updatedAt = now;
		return cart;
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

	public UserAccount getUser() {
		return user;
	}

	public void setUser(UserAccount user) {
		this.user = user;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public List<CartItem> getItems() {
		return items;
	}

	public void setItems(List<CartItem> items) {
		this.items = items;
	}

	public void addItem(CartItem item) {
		items.add(item);
		item.setCart(this);
	}

	public void removeItem(CartItem item) {
		items.remove(item);
		item.setCart(null);
	}

	@Override
	public String toString() {
		return "Cart{id=" + id + '}';
	}
}
