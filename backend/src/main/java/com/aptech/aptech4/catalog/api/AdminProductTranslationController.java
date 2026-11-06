package com.aptech.aptech4.catalog.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.catalog.application.AdminCatalogTranslationService;
import com.aptech.aptech4.common.api.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/products/{id}/translations")
@Tag(name = "Admin Product Translations", description = "Bilingual product name/description (ROLE_ADMIN)")
public class AdminProductTranslationController {

	private final AdminCatalogTranslationService translationService;

	public AdminProductTranslationController(AdminCatalogTranslationService translationService) {
		this.translationService = translationService;
	}

	@GetMapping
	@Operation(summary = "Get product translations (vi + en)",
			description = "VI mirrors canonical base columns. Missing EN returns null name/description.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Translation map",
					content = @Content(schema = @Schema(implementation = EntityTranslationsResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Product not found",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<EntityTranslationsResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(translationService.getProductTranslations(id));
	}

	@PutMapping
	@Operation(summary = "Upsert product translations",
			description = "Requires translations.vi.name. Updates canonical VI base columns + translation rows atomically. "
					+ "Does not accept SKU, price, inventory, or other invariants.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated",
					content = @Content(schema = @Schema(implementation = EntityTranslationsResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation / unsupported locale"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Product not found")
	})
	public ResponseEntity<EntityTranslationsResponse> put(
			@PathVariable UUID id,
			@Valid @RequestBody EntityTranslationsUpdateRequest request
	) {
		return ResponseEntity.ok(translationService.putProductTranslations(id, request));
	}
}
