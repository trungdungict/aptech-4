package com.aptech.aptech4.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

class OpenApiConfigTest {

	@Test
	void openApiDeclaresInfoAndBearerJwtScheme() {
		OpenAPI openAPI = new OpenApiConfig().aptech4OpenApi();

		assertEquals("Aptech-4 E-commerce API", openAPI.getInfo().getTitle());
		assertEquals("0.0.1-SNAPSHOT", openAPI.getInfo().getVersion());
		assertTrue(openAPI.getInfo().getDescription().contains("/api/v1"));

		SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
		assertNotNull(scheme);
		assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
		assertEquals("bearer", scheme.getScheme());
		assertEquals("JWT", scheme.getBearerFormat());

		assertNotNull(openAPI.getSecurity());
		assertTrue(openAPI.getSecurity().stream()
			.anyMatch(req -> req.containsKey(OpenApiConfig.BEARER_SCHEME)));
	}
}
