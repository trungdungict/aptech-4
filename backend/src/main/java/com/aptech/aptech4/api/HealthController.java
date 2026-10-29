package com.aptech.aptech4.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Liveness/readiness style checks (no secrets)")
public class HealthController {

	@GetMapping("/health")
	@SecurityRequirements // public — clears default bearer requirement
	@Operation(summary = "API health", description = "Returns OK when the application process is up.")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of(
			"status", "OK",
			"service", "aptech4-api"
		));
	}
}
