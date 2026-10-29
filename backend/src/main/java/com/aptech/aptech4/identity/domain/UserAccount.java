package com.aptech.aptech4.identity.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Maps to {@code users}. Named {@code UserAccount} to avoid clashing with security {@code User}.
 */
@Entity
@Table(name = "users")
public class UserAccount extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "email", nullable = false, unique = true, length = 320)
	private String email;

	@JsonIgnore
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 200)
	private String fullName;

	@Column(name = "enabled", nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(
			name = "user_roles",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id")
	)
	private Set<Role> roles = new HashSet<>();

	protected UserAccount() {
	}

	public static UserAccount createNew(String email, String passwordHash, String fullName) {
		UserAccount account = new UserAccount();
		account.email = email;
		account.passwordHash = passwordHash;
		account.fullName = fullName;
		account.enabled = true;
		account.createdAt = Instant.now();
		account.updatedAt = Instant.now();
		return account;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
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

	public Set<Role> getRoles() {
		return roles;
	}

	public void setRoles(Set<Role> roles) {
		this.roles = roles;
	}

	@Override
	public String toString() {
		return "UserAccount{id=" + id + ", email='" + email + "', enabled=" + enabled + '}';
	}
}
