package com.aptech.aptech4.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.identity.security.CorsProperties;
import com.aptech.aptech4.identity.security.JwtProperties;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Fail-fast rules without a full Spring Boot slice (Boot 4 moved validation autoconfig packages).
 */
class JwtPropertiesContextFailFastTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void blankAccessSecretFailsBeanValidation() {
		JwtProperties props = validProps();
		props.setAccessSecret(" ");
		assertFalse(validator.validate(props).isEmpty());
	}

	@Test
	void shortAccessSecretFailsBeanValidation() {
		JwtProperties props = validProps();
		props.setAccessSecret("too-short");
		assertFalse(validator.validate(props).isEmpty());
	}

	@Test
	void validSecretsPassBeanValidation() {
		assertTrue(validator.validate(validProps()).isEmpty());
	}

	@Test
	void identicalSecretsFailPostConstruct() {
		JwtProperties props = validProps();
		props.setRefreshSecret(props.getAccessSecret());
		assertThrows(IllegalStateException.class, props::rejectWeakOrIdenticalSecrets);
	}

	@Test
	void corsWildcardWithCredentialsFailsPostConstruct() {
		CorsProperties cors = new CorsProperties();
		cors.setAllowedOrigins("*");
		cors.setAllowCredentials(true);
		assertThrows(IllegalStateException.class, cors::rejectWildcardWithCredentials);
	}

	private static JwtProperties validProps() {
		JwtProperties props = new JwtProperties();
		props.setAccessSecret("access-secret-value-32chars-minimum!!");
		props.setRefreshSecret("refresh-secret-value-32chars-minimum!");
		props.setIssuer("aptech4-api");
		return props;
	}
}
