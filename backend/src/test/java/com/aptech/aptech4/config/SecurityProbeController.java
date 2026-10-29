package com.aptech.aptech4.config;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller for authorization matrix checks. Not part of main sources.
 * Production admin APIs must live under {@code /api/v1/admin/**} (filter chain) and/or
 * use {@code @PreAuthorize}; do not copy these probe paths into production.
 */
@RestController
@RequestMapping("/api/v1/__probe")
public class SecurityProbeController {

	@GetMapping("/me")
	@PreAuthorize("isAuthenticated()")
	Map<String, Object> authenticated() {
		return Map.of("authenticated", true);
	}

	/**
	 * Customer-only surface. ADMIN is intentionally not granted access — admin tooling
	 * uses {@code /api/v1/admin/**}, not customer ownership shortcuts.
	 */
	@GetMapping("/customer")
	@PreAuthorize("hasRole('CUSTOMER')")
	Map<String, String> customerOnly() {
		return Map.of("role", "CUSTOMER");
	}

	@GetMapping("/admin")
	@PreAuthorize("hasRole('ADMIN')")
	Map<String, String> adminOnly() {
		return Map.of("role", "ADMIN");
	}

	/**
	 * Ownership check: JWT principal {@code userId} must match path. Role ADMIN does not bypass.
	 */
	@GetMapping("/customers/{userId}")
	@PreAuthorize("isAuthenticated() and principal.userId == #userId")
	Map<String, Object> customerOwnedResource(@PathVariable UUID userId) {
		return Map.of("userId", userId.toString(), "owner", true);
	}
}
