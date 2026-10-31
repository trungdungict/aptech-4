package com.aptech.aptech4.promotion.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.promotion.application.PromotionStrategyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promotions/strategies")
@Tag(name = "Admin Promotion Strategies", description = "Reusable promotion rule templates (ROLE_ADMIN)")
public class AdminPromotionStrategyController {

	private final PromotionStrategyService strategyService;

	public AdminPromotionStrategyController(PromotionStrategyService strategyService) {
		this.strategyService = strategyService;
	}

	@GetMapping
	@Operation(summary = "List promotion strategies")
	public ResponseEntity<List<PromotionStrategyResponse>> list() {
		return ResponseEntity.ok(strategyService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a promotion strategy")
	public ResponseEntity<PromotionStrategyResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(strategyService.get(id));
	}

	@PostMapping
	@Operation(summary = "Create a promotion strategy")
	public ResponseEntity<PromotionStrategyResponse> create(@Valid @RequestBody PromotionStrategyWriteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(strategyService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a promotion strategy")
	public ResponseEntity<PromotionStrategyResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody PromotionStrategyWriteRequest request
	) {
		return ResponseEntity.ok(strategyService.update(id, request));
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Activate or deactivate a promotion strategy")
	public ResponseEntity<PromotionStrategyResponse> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody PromotionStrategyStatusRequest request
	) {
		return ResponseEntity.ok(strategyService.updateStatus(id, request));
	}
}
