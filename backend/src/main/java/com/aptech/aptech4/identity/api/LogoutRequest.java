package com.aptech.aptech4.identity.api;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
		@NotBlank
		String refreshToken
) {
}
