package com.aptech.aptech4.catalog.api;

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

import com.aptech.aptech4.catalog.application.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin Categories", description = "Admin category management (ROLE_ADMIN)")
public class AdminCategoryController {

	private final CategoryService categoryService;

	public AdminCategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	@Operation(summary = "List all categories including inactive")
	public ResponseEntity<List<CategoryResponse>> list() {
		return ResponseEntity.ok(categoryService.listAdminAll());
	}

	@PostMapping
	@Operation(summary = "Create a category")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Created",
					content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "409", description = "Duplicate name")
	})
	public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a category")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated",
					content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "409", description = "Duplicate name")
	})
	public ResponseEntity<CategoryResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody CategoryUpdateRequest request
	) {
		return ResponseEntity.ok(categoryService.update(id, request));
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Activate or deactivate a category (soft-disable; no hard delete)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Status updated",
					content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found")
	})
	public ResponseEntity<CategoryResponse> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody CategoryStatusRequest request
	) {
		return ResponseEntity.ok(categoryService.updateStatus(id, request));
	}
}
