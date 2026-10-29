package com.aptech.aptech4.cart.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.cart.application.CartService;
import com.aptech.aptech4.common.api.ApiError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Cart", description = "Authenticated customer cart (ownership from JWT; no inventory reservation)")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	@Operation(summary = "Get current customer's cart (creates empty cart if missing)")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cart",
					content = @Content(schema = @Schema(implementation = CartResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden (non-CUSTOMER)")
	})
	public ResponseEntity<CartResponse> getCart() {
		return ResponseEntity.ok(cartService.getMyCart());
	}

	@PostMapping("/items")
	@Operation(summary = "Add product to cart",
			description = "Merges quantity when the product is already in the cart. "
					+ "Product must be active with an active category. Does not reserve inventory.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated cart"),
			@ApiResponse(responseCode = "400", description = "Validation / product unavailable / quantity limit",
					content = @Content(schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
		return ResponseEntity.ok(cartService.addItem(request));
	}

	@PatchMapping("/items/{itemId}")
	@Operation(summary = "Update cart item quantity",
			description = "Rejected if the product is no longer purchasable. Remove the line instead.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated cart"),
			@ApiResponse(responseCode = "400", description = "Validation / product unavailable"),
			@ApiResponse(responseCode = "404", description = "Item missing or not owned by caller")
	})
	public ResponseEntity<CartResponse> updateItem(
			@PathVariable UUID itemId,
			@Valid @RequestBody UpdateCartItemRequest request
	) {
		return ResponseEntity.ok(cartService.updateItem(itemId, request));
	}

	@DeleteMapping("/items/{itemId}")
	@Operation(summary = "Remove one cart item")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated cart"),
			@ApiResponse(responseCode = "404", description = "Item missing or not owned by caller")
	})
	public ResponseEntity<CartResponse> removeItem(@PathVariable UUID itemId) {
		return ResponseEntity.ok(cartService.removeItem(itemId));
	}

	@DeleteMapping("/items")
	@Operation(summary = "Clear all cart items")
	@ApiResponse(responseCode = "200", description = "Empty cart")
	public ResponseEntity<CartResponse> clearItems() {
		return ResponseEntity.ok(cartService.clearItems());
	}
}
