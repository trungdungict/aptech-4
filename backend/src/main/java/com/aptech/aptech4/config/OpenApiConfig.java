package com.aptech.aptech4.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	public static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	OpenAPI aptech4OpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Aptech-4 E-commerce API")
				.description("""
						REST API for the Aptech semester 4 e-commerce graduation project.
						Base path: `/api/v1`. Controllers expose DTOs only — never JPA entities.
						Use Authorize with a JWT access token for protected operations (P04+).
						""")
				.version("0.0.1-SNAPSHOT")
				.contact(new Contact().name("Aptech-4").email("admin@aptech4.local")))
			.components(new Components()
				.addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
					.name(BEARER_SCHEME)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("JWT access token (Authorization: Bearer <token>)")))
			// Default requirement documents that most future operations are protected;
			// public operations (e.g. health) override with @SecurityRequirements({}).
			.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
