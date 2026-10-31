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

import com.aptech.aptech4.promotion.application.PromotionCampaignService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promotions/campaigns")
@Tag(name = "Admin Promotion Campaigns", description = "Scheduled promotion instances (ROLE_ADMIN)")
public class AdminPromotionCampaignController {

	private final PromotionCampaignService campaignService;

	public AdminPromotionCampaignController(PromotionCampaignService campaignService) {
		this.campaignService = campaignService;
	}

	@GetMapping
	@Operation(summary = "List promotion campaigns")
	public ResponseEntity<List<PromotionCampaignResponse>> list() {
		return ResponseEntity.ok(campaignService.list());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a promotion campaign")
	public ResponseEntity<PromotionCampaignResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(campaignService.get(id));
	}

	@PostMapping
	@Operation(summary = "Create a promotion campaign (DRAFT)")
	public ResponseEntity<PromotionCampaignResponse> create(@Valid @RequestBody PromotionCampaignWriteRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a promotion campaign (including strategy while ACTIVE)")
	public ResponseEntity<PromotionCampaignResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody PromotionCampaignWriteRequest request
	) {
		return ResponseEntity.ok(campaignService.update(id, request));
	}

	@PutMapping("/{id}/eligibility")
	@Operation(summary = "Replace campaign product/category eligibility")
	public ResponseEntity<PromotionCampaignResponse> replaceEligibility(
			@PathVariable UUID id,
			@Valid @RequestBody PromotionEligibilityRequest request
	) {
		return ResponseEntity.ok(campaignService.replaceEligibility(id, request));
	}

	@PostMapping("/{id}/activate")
	@Operation(summary = "Activate a campaign (manual; may keep future startsAt)")
	public ResponseEntity<PromotionCampaignResponse> activate(@PathVariable UUID id) {
		return ResponseEntity.ok(campaignService.activate(id));
	}

	@PostMapping("/{id}/pause")
	@Operation(summary = "Pause a campaign")
	public ResponseEntity<PromotionCampaignResponse> pause(@PathVariable UUID id) {
		return ResponseEntity.ok(campaignService.pause(id));
	}

	@PostMapping("/{id}/schedule")
	@Operation(summary = "Mark a campaign SCHEDULED (startsAt must be in the future)")
	public ResponseEntity<PromotionCampaignResponse> schedule(@PathVariable UUID id) {
		return ResponseEntity.ok(campaignService.schedule(id));
	}

	@PostMapping("/{id}/preview")
	@Operation(summary = "Preview schedule effectiveness (discount math deferred to P22)")
	public ResponseEntity<CampaignPreviewResponse> preview(
			@PathVariable UUID id,
			@RequestBody(required = false) CampaignPreviewRequest request
	) {
		return ResponseEntity.ok(campaignService.preview(id, request));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a campaign (cascades placements and eligibility)")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		campaignService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
