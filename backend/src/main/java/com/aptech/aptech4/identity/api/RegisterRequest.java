package com.aptech.aptech4.identity.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(
		@NotBlank
		@Email
		@Size(max = 320)
		String email,

		@NotBlank
		@Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
		String password,

		@NotBlank
		@Size(max = 200)
		String fullName
) {
}
