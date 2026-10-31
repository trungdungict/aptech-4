package com.aptech.aptech4.promotion.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.promotion.application.PromotionPlacementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promotions")
@Tag(name = "Admin Promotion Placements", description = "Storefront creatives for campaigns (ROLE_ADMIN)")
public class AdminPromotionPlacementController {

	private final PromotionPlacementService placementService;

	public AdminPromotionPlacementController(PromotionPlacementService placementService) {
		this.placementService = placementService;
	}

	@GetMapping("/campaigns/{campaignId}/placements")
	@Operation(summary = "List placements for a campaign")
	public ResponseEntity<List<PromotionPlacementResponse>> listByCampaign(@PathVariable UUID campaignId) {
		return ResponseEntity.ok(placementService.listByCampaign(campaignId));
	}

	@PostMapping("/campaigns/{campaignId}/placements")
	@Operation(summary = "Create a placement under a campaign")
	public ResponseEntity<PromotionPlacementResponse> create(
			@PathVariable UUID campaignId,
			@Valid @RequestBody PromotionPlacementWriteRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(placementService.create(campaignId, request));
	}

	@GetMapping("/placements/{id}")
	@Operation(summary = "Get a placement")
	public ResponseEntity<PromotionPlacementResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(placementService.get(id));
	}

	@PutMapping("/placements/{id}")
	@Operation(summary = "Update a placement")
	public ResponseEntity<PromotionPlacementResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody PromotionPlacementWriteRequest request
	) {
		return ResponseEntity.ok(placementService.update(id, request));
	}

	@DeleteMapping("/placements/{id}")
	@Operation(summary = "Delete a placement")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		placementService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
