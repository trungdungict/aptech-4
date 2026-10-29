package com.aptech.aptech4.identity.security;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JwtPropertiesValidationTest {

	@Test
	void identicalSecretsFailFast() {
		JwtProperties props = new JwtProperties();
		String secret = "same-secret-value-32chars-minimum!!";
		props.setAccessSecret(secret);
		props.setRefreshSecret(secret);

		assertThrows(IllegalStateException.class, props::rejectWeakOrIdenticalSecrets);
	}

	@Test
	void corsWildcardWithCredentialsFailFast() {
		CorsProperties cors = new CorsProperties();
		cors.setAllowedOrigins("*");
		cors.setAllowCredentials(true);

		assertThrows(IllegalStateException.class, cors::rejectWildcardWithCredentials);
	}
}
