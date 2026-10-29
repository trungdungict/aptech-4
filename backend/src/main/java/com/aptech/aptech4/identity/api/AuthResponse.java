package com.aptech.aptech4.identity.api;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UserResponse user
) {
	public record UserResponse(
			UUID id,
			String email,
			String fullName,
			List<String> roles
	) {
	}
}
