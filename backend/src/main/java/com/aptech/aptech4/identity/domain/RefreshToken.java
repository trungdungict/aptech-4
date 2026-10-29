package com.aptech.aptech4.identity.domain;

import java.time.Instant;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@JsonIgnore
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_id")
	private RefreshToken replacedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "user_agent", length = 512)
	private String userAgent;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	protected RefreshToken() {
	}

	public static RefreshToken createNew(UserAccount user, String tokenHash, Instant expiresAt) {
		RefreshToken token = new RefreshToken();
		token.user = user;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		token.createdAt = Instant.now();
		return token;
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
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

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public void setRevokedAt(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}

	public RefreshToken getReplacedBy() {
		return replacedBy;
	}

	public void setReplacedBy(RefreshToken replacedBy) {
		this.replacedBy = replacedBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	@Override
	public String toString() {
		return "RefreshToken{id=" + id + ", expiresAt=" + expiresAt + ", revokedAt=" + revokedAt + '}';
	}
}
