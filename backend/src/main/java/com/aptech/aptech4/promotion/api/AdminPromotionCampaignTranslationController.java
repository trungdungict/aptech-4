package com.aptech.aptech4.promotion.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.promotion.application.AdminPromotionTranslationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/promotions/campaigns/{id}/translations")
@Tag(name = "Admin Campaign Translations", description = "Bilingual campaign name (ROLE_ADMIN); notes stay non-localized")
public class AdminPromotionCampaignTranslationController {

	private final AdminPromotionTranslationService translationService;

	public AdminPromotionCampaignTranslationController(AdminPromotionTranslationService translationService) {
		this.translationService = translationService;
	}

	@GetMapping
	@Operation(summary = "Get campaign name translations (vi + en)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Translation map",
					content = @Content(schema = @Schema(implementation = NameTranslationsResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<NameTranslationsResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(translationService.getCampaignTranslations(id));
	}

	@PutMapping
	@Operation(summary = "Upsert campaign name translations",
			description = "Requires translations.vi. Does not change slug, status, schedule, eligibility, or notes.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated"),
			@ApiResponse(responseCode = "400", description = "Validation / unsupported locale"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found")
	})
	public ResponseEntity<NameTranslationsResponse> put(
			@PathVariable UUID id,
			@Valid @RequestBody NameTranslationsUpdateRequest request
	) {
		return ResponseEntity.ok(translationService.putCampaignTranslations(id, request));
	}
}
