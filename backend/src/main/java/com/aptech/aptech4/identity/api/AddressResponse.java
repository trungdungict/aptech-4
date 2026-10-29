package com.aptech.aptech4.identity.api;

import java.util.UUID;

public record AddressResponse(
		UUID id,
		String label,
		String recipientName,
		String line1,
		String line2,
		String city,
		String stateRegion,
		String postalCode,
		String countryCode,
		String phone,
		boolean defaultAddress
) {
}
