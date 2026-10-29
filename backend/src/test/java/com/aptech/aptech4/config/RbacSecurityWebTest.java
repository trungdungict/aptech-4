package com.aptech.aptech4.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.aptech.aptech4.api.HealthController;
import com.aptech.aptech4.identity.security.JwtAuthenticationFilter;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RestAccessDeniedHandler;
import com.aptech.aptech4.identity.security.RestAuthenticationEntryPoint;

@org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest(controllers = {
		HealthController.class,
		SecurityProbeController.class,
		AdminPathProbeController.class
})
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
		"aptech.security.jwt.access-ttl-seconds=900",
		"aptech.security.jwt.refresh-ttl-seconds=3600",
		"aptech.security.cors.allowed-origins=http://localhost:5173",
		"aptech.security.cors.allow-credentials=true"
})
class RbacSecurityWebTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	@Test
	void adminRouteWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/v1/admin/__probe"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void customerTokenOnAdminRouteReturns403() throws Exception {
		String token = jwtService.createAccessToken(UUID.randomUUID(), "c@example.com", List.of("CUSTOMER"));
		mockMvc.perform(get("/api/v1/admin/__probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void adminTokenOnAdminRouteAllowed() throws Exception {
		String token = jwtService.createAccessToken(UUID.randomUUID(), "a@example.com", List.of("ADMIN"));
		mockMvc.perform(get("/api/v1/admin/__probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.scope").value("admin-path"));
	}

	@Test
	void customerTokenOnCustomerRouteAllowed() throws Exception {
		String token = jwtService.createAccessToken(UUID.randomUUID(), "c@example.com", List.of("CUSTOMER"));
		mockMvc.perform(get("/api/v1/__probe/customer")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("CUSTOMER"));
	}

	@Test
	void adminTokenOnCustomerOnlyRouteForbidden() throws Exception {
		String token = jwtService.createAccessToken(UUID.randomUUID(), "a@example.com", List.of("ADMIN"));
		mockMvc.perform(get("/api/v1/__probe/customer")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void ownerCanAccessOwnResourceButNotOthers() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();
		String token = jwtService.createAccessToken(ownerId, "owner@example.com", List.of("CUSTOMER"));

		mockMvc.perform(get("/api/v1/__probe/customers/" + ownerId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owner").value(true));

		mockMvc.perform(get("/api/v1/__probe/customers/" + otherId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminDoesNotBypassCustomerOwnership() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		String adminToken = jwtService.createAccessToken(adminId, "admin@example.com", List.of("ADMIN"));

		mockMvc.perform(get("/api/v1/__probe/customers/" + ownerId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void refreshTokenUseRejectedOnAdminRoute() throws Exception {
		String refresh = jwtService.createRefreshToken(UUID.randomUUID());
		mockMvc.perform(get("/api/v1/admin/__probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + refresh))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void forgedAdminRoleInUnsignedPayloadRejected() throws Exception {
		// Access token signed correctly but with CUSTOMER only — client cannot "add" ADMIN via body.
		String customer = jwtService.createAccessToken(UUID.randomUUID(), "c@example.com", List.of("CUSTOMER"));
		mockMvc.perform(get("/api/v1/__probe/admin")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer)
				.header("X-Role", "ADMIN")
				.param("role", "ADMIN"))
			.andExpect(status().isForbidden());
	}
}
