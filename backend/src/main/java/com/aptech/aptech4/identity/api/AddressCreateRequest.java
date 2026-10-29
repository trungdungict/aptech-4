package com.aptech.aptech4.identity.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressCreateRequest(
		@Size(max = 80)
		String label,

		@NotBlank
		@Size(max = 200)
		String recipientName,

		@NotBlank
		@Size(max = 200)
		String line1,

		@Size(max = 200)
		String line2,

		@NotBlank
		@Size(max = 120)
		String city,

		@Size(max = 120)
		String stateRegion,

		@Size(max = 32)
		String postalCode,

		@Size(min = 2, max = 2)
		String countryCode,

		@Size(max = 32)
		String phone,

		Boolean defaultAddress
) {
}
