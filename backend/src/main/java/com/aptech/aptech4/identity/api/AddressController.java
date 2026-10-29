package com.aptech.aptech4.identity.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.identity.application.AddressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Minimal customer address API required for checkout ({@code addressId}).
 * Schema existed since P03; REST surface added for P09-T04 storefront checkout.
 */
@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Addresses", description = "Customer shipping addresses (owned by JWT subject)")
public class AddressController {

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	@GetMapping
	@Operation(summary = "List my addresses")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Addresses for the current customer"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<List<AddressResponse>> list() {
		return ResponseEntity.ok(addressService.listMine());
	}

	@PostMapping
	@Operation(summary = "Create a shipping address")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Created"),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<AddressResponse> create(@Valid @RequestBody AddressCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(addressService.create(request));
	}
}
