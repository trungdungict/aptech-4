package com.aptech.aptech4.config;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only probe under {@code /api/v1/admin/**} to exercise the filter-chain admin matcher.
 */
@RestController
@RequestMapping("/api/v1/admin/__probe")
public class AdminPathProbeController {

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	Map<String, String> adminPath() {
		return Map.of("scope", "admin-path");
	}
}
