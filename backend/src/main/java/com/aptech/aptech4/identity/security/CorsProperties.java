package com.aptech.aptech4.identity.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "aptech.security.cors")
public class CorsProperties {

	/**
	 * Comma-separated origins, e.g. {@code http://localhost:5173,http://localhost:3000}.
	 * Wildcard {@code *} is rejected when {@code allowCredentials=true}.
	 */
	@NotBlank
	private String allowedOrigins;

	private boolean allowCredentials = true;

	@PostConstruct
	public void rejectWildcardWithCredentials() {
		boolean hasWildcard = originList().stream().anyMatch("*"::equals);
		if (hasWildcard && allowCredentials) {
			throw new IllegalStateException(
					"CORS must not combine allowedOrigins=* with allowCredentials=true");
		}
	}

	public List<String> originList() {
		return Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
	}

	public String getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public boolean isAllowCredentials() {
		return allowCredentials;
	}

	public void setAllowCredentials(boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}
}
