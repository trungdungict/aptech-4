package com.aptech.aptech4.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Public product discovery (active products in active categories)")
@SecurityRequirements
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	@Operation(summary = "List/search active products",
			description = """
					Zero-based page index. Default size=20, max size=100 (larger sizes → 400).
					Sort whitelist: name, price, createdAt (format: field,asc|desc). Stable id tie-breaker always applied.
					Only active products whose category is active are returned.
					categoryId must reference an active category or the API returns 404.
					""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Page of products"),
			@ApiResponse(responseCode = "400", description = "Invalid page/size/sort/price range",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "categoryId missing or inactive",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<PageResponse<ProductResponse>> list(
			@Parameter(description = "Keyword matched against name and description (case-insensitive)")
			@RequestParam(required = false) String q,
			@Parameter(description = "Active category id filter")
			@RequestParam(required = false) UUID categoryId,
			@Parameter(description = "Minimum unit price (inclusive); must be >= 0")
			@RequestParam(required = false) BigDecimal minPrice,
			@Parameter(description = "Maximum unit price (inclusive); must be >= 0 and >= minPrice")
			@RequestParam(required = false) BigDecimal maxPrice,
			@Parameter(description = "When true, only products with inventory.quantity_on_hand > 0; "
					+ "when false, missing inventory or quantity 0")
			@RequestParam(required = false) Boolean inStock,
			@Parameter(description = "Zero-based page index")
			@RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size (1.." + ProductService.MAX_PAGE_SIZE + ")")
			@RequestParam(defaultValue = "" + ProductService.DEFAULT_PAGE_SIZE) int size,
			@Parameter(description = "Sort as field,direction — whitelist: name, price, createdAt")
			@RequestParam(required = false) String sort
	) {
		return ResponseEntity.ok(productService.listPublic(
				q, categoryId, minPrice, maxPrice, inStock, page, size, sort
		));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get an active product")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Found",
					content = @Content(schema = @Schema(implementation = ProductResponse.class))),
			@ApiResponse(responseCode = "404", description = "Missing, inactive, or category inactive",
					content = @Content(schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<ProductResponse> get(@PathVariable UUID id) {
		return ResponseEntity.ok(productService.getPublic(id));
	}
}
