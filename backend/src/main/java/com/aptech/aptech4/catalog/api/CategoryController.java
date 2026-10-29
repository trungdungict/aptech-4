package com.aptech.aptech4.catalog.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.catalog.application.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Public catalog categories (active only)")
@SecurityRequirements
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	@Operation(summary = "List active categories")
	@ApiResponse(responseCode = "200", description = "Active categories",
			content = @Content(schema = @Schema(implementation = CategoryResponse.class)))
	public ResponseEntity<List<CategoryResponse>> list() {
		return ResponseEntity.ok(categoryService.listPublicActive());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get an active category")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found",
					content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
			@ApiResponse(responseCode = "404", description = "Missing or inactive")
	})
	public ResponseEntity<CategoryResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(categoryService.getPublicActive(id));
	}
}
