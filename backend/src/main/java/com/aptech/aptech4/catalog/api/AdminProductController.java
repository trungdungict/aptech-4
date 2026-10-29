package com.aptech.aptech4.catalog.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.common.api.ApiError;
import com.aptech.aptech4.common.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin Products", description = "Admin product management (ROLE_ADMIN). Soft-disable only — no hard delete.")
public class AdminProductController {

	private final ProductService productService;

	public AdminProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	@Operation(summary = "List products for admin (includes inactive)",
			description = "Zero-based page. Default size=20, max=100. Sort whitelist: name, price, createdAt.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of products"),
			@ApiResponse(responseCode = "400", description = "Invalid page/size/sort",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "categoryId does not exist",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<PageResponse<ProductResponse>> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) UUID categoryId,
			@Parameter(description = "Filter by product.active")
			@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + ProductService.DEFAULT_PAGE_SIZE) int size,
			@RequestParam(required = false) String sort
	) {
		return ResponseEntity.ok(productService.listAdmin(q, categoryId, active, page, size, sort));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get any product by id (including inactive)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found",
					content = @Content(schema = @Schema(implementation = ProductResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(productService.getAdmin(id));
	}

	@PostMapping
	@Operation(summary = "Create a product",
			description = "Requires an active category. Does not create or set inventory (P06). New products start active=true.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Created",
					content = @Content(schema = @Schema(implementation = ProductResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed / inactive category"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "409", description = "Duplicate SKU")
	})
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a product",
			description = "Category must be active. Inventory quantity is never updated via this API.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated",
					content = @Content(schema = @Schema(implementation = ProductResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed / inactive category"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "409", description = "Duplicate SKU")
	})
	public ResponseEntity<ProductResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody ProductUpdateRequest request
	) {
		return ResponseEntity.ok(productService.update(id, request));
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Activate or deactivate a product (soft-disable; no hard delete)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Status updated",
					content = @Content(schema = @Schema(implementation = ProductResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found")
	})
	public ResponseEntity<ProductResponse> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody ProductStatusRequest request
	) {
		return ResponseEntity.ok(productService.updateStatus(id, request));
	}
}
