package com.aptech.aptech4.identity.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "aptech.security.jwt")
public class JwtProperties {

	public static final int MIN_SECRET_LENGTH = 32;

	@NotBlank
	@Size(min = MIN_SECRET_LENGTH, message = "JWT access secret must be at least 32 characters")
	private String accessSecret;

	@NotBlank
	@Size(min = MIN_SECRET_LENGTH, message = "JWT refresh secret must be at least 32 characters")
	private String refreshSecret;

	@NotBlank
	private String issuer = "aptech4-api";

	@Min(60)
	private long accessTtlSeconds = 900;

	@Min(60)
	private long refreshTtlSeconds = 604800;

	@PostConstruct
	public void rejectWeakOrIdenticalSecrets() {
		if (accessSecret.equals(refreshSecret)) {
			throw new IllegalStateException(
					"JWT access secret and refresh secret must be different (no shared secret)");
		}
	}

	public String getAccessSecret() {
		return accessSecret;
	}

	public void setAccessSecret(String accessSecret) {
		this.accessSecret = accessSecret;
	}

	public String getRefreshSecret() {
		return refreshSecret;
	}

	public void setRefreshSecret(String refreshSecret) {
		this.refreshSecret = refreshSecret;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public long getAccessTtlSeconds() {
		return accessTtlSeconds;
	}

	public void setAccessTtlSeconds(long accessTtlSeconds) {
		this.accessTtlSeconds = accessTtlSeconds;
	}

	public long getRefreshTtlSeconds() {
		return refreshTtlSeconds;
	}

	public void setRefreshTtlSeconds(long refreshTtlSeconds) {
		this.refreshTtlSeconds = refreshTtlSeconds;
	}
}
