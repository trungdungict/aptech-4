package com.aptech.aptech4.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.aptech.aptech4.api.HealthController;
import com.aptech.aptech4.identity.security.JwtAuthenticationFilter;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RestAccessDeniedHandler;
import com.aptech.aptech4.identity.security.RestAuthenticationEntryPoint;

/**
 * Health remains public; other /api/v1 paths stay authenticated under JWT security.
 */
@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest(controllers = HealthController.class)
@Import({
		SecurityConfig.class,
		JwtService.class,
		JwtAuthenticationFilter.class,
		RestAuthenticationEntryPoint.class,
		RestAccessDeniedHandler.class
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestPropertySource(properties = {
		"aptech.security.jwt.access-secret=access-secret-value-32chars-minimum!!",
		"aptech.security.jwt.refresh-secret=refresh-secret-value-32chars-minimum!",
		"aptech.security.jwt.issuer=aptech4-api",
		"aptech.security.cors.allowed-origins=http://localhost:5173",
		"springdoc.api-docs.enabled=true",
		"springdoc.swagger-ui.enabled=true",
		"springdoc.paths-to-match=/api/v1/**",
		"springdoc.show-actuator=false"
})
class OpenApiSecurityWebTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OK"));
	}

	@Test
	void unknownApiPathRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/does-not-exist"))
			.andExpect(status().isUnauthorized());
	}
}
