package com.aptech.aptech4.identity.domain;

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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "addresses")
public class Address extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "label", length = 80)
	private String label;

	@Column(name = "recipient_name", nullable = false, length = 200)
	private String recipientName;

	@Column(name = "line1", nullable = false, length = 200)
	private String line1;

	@Column(name = "line2", length = 200)
	private String line2;

	@Column(name = "city", nullable = false, length = 120)
	private String city;

	@Column(name = "state_region", length = 120)
	private String stateRegion;

	@Column(name = "postal_code", length = 32)
	private String postalCode;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode = "VN";

	@Column(name = "phone", length = 32)
	private String phone;

	@Column(name = "is_default", nullable = false)
	private boolean defaultAddress;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Address() {
	}

	public static Address createNew(
			UserAccount user,
			String recipientName,
			String line1,
			String line2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String phone,
			boolean defaultAddress
	) {
		Address address = new Address();
		address.user = user;
		address.recipientName = recipientName;
		address.line1 = line1;
		address.line2 = line2;
		address.city = city;
		address.stateRegion = stateRegion;
		address.postalCode = postalCode;
		address.countryCode = countryCode == null || countryCode.isBlank() ? "VN" : countryCode;
		address.phone = phone;
		address.defaultAddress = defaultAddress;
		Instant now = Instant.now();
		address.createdAt = now;
		address.updatedAt = now;
		return address;
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

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public void setRecipientName(String recipientName) {
		this.recipientName = recipientName;
	}

	public String getLine1() {
		return line1;
	}

	public void setLine1(String line1) {
		this.line1 = line1;
	}

	public String getLine2() {
		return line2;
	}

	public void setLine2(String line2) {
		this.line2 = line2;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getStateRegion() {
		return stateRegion;
	}

	public void setStateRegion(String stateRegion) {
		this.stateRegion = stateRegion;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public void setPostalCode(String postalCode) {
		this.postalCode = postalCode;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public boolean isDefaultAddress() {
		return defaultAddress;
	}

	public void setDefaultAddress(boolean defaultAddress) {
		this.defaultAddress = defaultAddress;
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
		return "Address{id=" + id + ", city='" + city + "'}";
	}
}
