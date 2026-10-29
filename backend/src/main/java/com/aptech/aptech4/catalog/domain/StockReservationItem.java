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
		name = "stock_reservation_items",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_stock_reservation_items_res_product",
				columnNames = { "reservation_id", "product_id" }
		)
)
public class StockReservationItem extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false)
	private StockReservation reservation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	protected StockReservationItem() {
	}

	public static StockReservationItem of(StockReservation reservation, Product product, int quantity) {
		StockReservationItem item = new StockReservationItem();
		item.reservation = reservation;
		item.product = product;
		item.quantity = quantity;
		return item;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public StockReservation getReservation() {
		return reservation;
	}

	public void setReservation(StockReservation reservation) {
		this.reservation = reservation;
	}

	public Product getProduct() {
		return product;
	}

	public int getQuantity() {
		return quantity;
	}

	@Override
	public String toString() {
		return "StockReservationItem{id=" + id + ", quantity=" + quantity + '}';
	}
}
