package com.aptech.aptech4.order.domain;

import java.math.BigDecimal;
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

@Entity
@Table(name = "order_items")
public class OrderItem extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private ShopOrder order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@Column(name = "product_sku", nullable = false, length = 64)
	private String productSku;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "line_total", nullable = false, precision = 12, scale = 2)
	private BigDecimal lineTotal;

	protected OrderItem() {
	}

	public static OrderItem snapshot(Product product, int quantity, BigDecimal unitPrice) {
		OrderItem item = new OrderItem();
		item.product = product;
		item.productSku = product.getSku();
		item.productName = product.getName();
		item.unitPrice = unitPrice;
		item.quantity = quantity;
		item.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
		return item;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public ShopOrder getOrder() {
		return order;
	}

	public void setOrder(ShopOrder order) {
		this.order = order;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public String getProductSku() {
		return productSku;
	}

	public void setProductSku(String productSku) {
		this.productSku = productSku;
	}

	public String getProductName() {
		return productName;
	}

	public void setProductName(String productName) {
		this.productName = productName;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}

	public void setLineTotal(BigDecimal lineTotal) {
		this.lineTotal = lineTotal;
	}

	@Override
	public String toString() {
		return "OrderItem{id=" + id + ", productSku='" + productSku + "', quantity=" + quantity + '}';
	}
}
