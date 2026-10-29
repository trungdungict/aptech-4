package com.aptech.aptech4.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.identity.application.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, refresh rotation, and logout")
@SecurityRequirements
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@Operation(summary = "Register a customer account")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Registered",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "409", description = "Email already registered")
	})
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	@Operation(summary = "Login and receive access + refresh JWTs")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Authenticated",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "401", description = "Invalid email or password")
	})
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate refresh token and issue a new access token",
			description = "Clients must replace the previous refresh token with the newly returned one.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rotated",
					content = @Content(schema = @Schema(implementation = AuthResponse.class))),
			@ApiResponse(responseCode = "400", description = "Validation failed"),
			@ApiResponse(responseCode = "401", description = "Invalid refresh token")
	})
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
		return ResponseEntity.ok(authService.refresh(request));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke a refresh token (this session only)",
			description = "Does not revoke outstanding access JWTs (stateless; no denylist). Idempotent.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Revoked or already inactive"),
			@ApiResponse(responseCode = "400", description = "Validation failed")
	})
	public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request);
		return ResponseEntity.noContent().build();
	}
}
