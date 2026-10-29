package com.aptech.aptech4.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps to {@code orders}. Named {@code ShopOrder} to avoid clashing with SQL/JPA keyword {@code Order}.
 */
@Entity
@Table(name = "orders")
public class ShopOrder extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private OrderStatus status;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "currency", nullable = false, length = 3)
	private String currency = "VND";

	@Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotalAmount;

	@Column(name = "shipping_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal shippingAmount = BigDecimal.ZERO;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@Column(name = "shipping_recipient_name", nullable = false, length = 200)
	private String shippingRecipientName;

	@Column(name = "shipping_line1", nullable = false, length = 200)
	private String shippingLine1;

	@Column(name = "shipping_line2", length = 200)
	private String shippingLine2;

	@Column(name = "shipping_city", nullable = false, length = 120)
	private String shippingCity;

	@Column(name = "shipping_state_region", length = 120)
	private String shippingStateRegion;

	@Column(name = "shipping_postal_code", length = 32)
	private String shippingPostalCode;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "shipping_country_code", nullable = false, length = 2)
	private String shippingCountryCode;

	@Column(name = "shipping_phone", length = 32)
	private String shippingPhone;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<OrderItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Payment> payments = new ArrayList<>();

	protected ShopOrder() {
	}

	/**
	 * Pending checkout order with shipping fields snapshotted from the caller's address.
	 */
	public static ShopOrder createPending(UserAccount user, Address address) {
		ShopOrder order = new ShopOrder();
		order.user = user;
		order.status = OrderStatus.PENDING_PAYMENT;
		order.shippingAmount = BigDecimal.ZERO;
		order.shippingRecipientName = address.getRecipientName();
		order.shippingLine1 = address.getLine1();
		order.shippingLine2 = address.getLine2();
		order.shippingCity = address.getCity();
		order.shippingStateRegion = address.getStateRegion();
		order.shippingPostalCode = address.getPostalCode();
		order.shippingCountryCode = address.getCountryCode();
		order.shippingPhone = address.getPhone();
		Instant now = Instant.now();
		order.placedAt = now;
		order.updatedAt = now;
		return order;
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

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getSubtotalAmount() {
		return subtotalAmount;
	}

	public void setSubtotalAmount(BigDecimal subtotalAmount) {
		this.subtotalAmount = subtotalAmount;
	}

	public BigDecimal getShippingAmount() {
		return shippingAmount;
	}

	public void setShippingAmount(BigDecimal shippingAmount) {
		this.shippingAmount = shippingAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public String getShippingRecipientName() {
		return shippingRecipientName;
	}

	public void setShippingRecipientName(String shippingRecipientName) {
		this.shippingRecipientName = shippingRecipientName;
	}

	public String getShippingLine1() {
		return shippingLine1;
	}

	public void setShippingLine1(String shippingLine1) {
		this.shippingLine1 = shippingLine1;
	}

	public String getShippingLine2() {
		return shippingLine2;
	}

	public void setShippingLine2(String shippingLine2) {
		this.shippingLine2 = shippingLine2;
	}

	public String getShippingCity() {
		return shippingCity;
	}

	public void setShippingCity(String shippingCity) {
		this.shippingCity = shippingCity;
	}

	public String getShippingStateRegion() {
		return shippingStateRegion;
	}

	public void setShippingStateRegion(String shippingStateRegion) {
		this.shippingStateRegion = shippingStateRegion;
	}

	public String getShippingPostalCode() {
		return shippingPostalCode;
	}

	public void setShippingPostalCode(String shippingPostalCode) {
		this.shippingPostalCode = shippingPostalCode;
	}

	public String getShippingCountryCode() {
		return shippingCountryCode;
	}

	public void setShippingCountryCode(String shippingCountryCode) {
		this.shippingCountryCode = shippingCountryCode;
	}

	public String getShippingPhone() {
		return shippingPhone;
	}

	public void setShippingPhone(String shippingPhone) {
		this.shippingPhone = shippingPhone;
	}

	public Instant getPlacedAt() {
		return placedAt;
	}

	public void setPlacedAt(Instant placedAt) {
		this.placedAt = placedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void setItems(List<OrderItem> items) {
		this.items = items;
	}

	public List<Payment> getPayments() {
		return payments;
	}

	public void setPayments(List<Payment> payments) {
		this.payments = payments;
	}

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	public void addPayment(Payment payment) {
		payments.add(payment);
		payment.setOrder(this);
	}

	@Override
	public String toString() {
		return "ShopOrder{id=" + id + ", status=" + status + '}';
	}
}
