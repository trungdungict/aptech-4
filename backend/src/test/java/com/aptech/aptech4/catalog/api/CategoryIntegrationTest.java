package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.aptech.aptech4.identity.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		String pgHost = env("IT_POSTGRES_HOST", "localhost");
		String pgPort = env("IT_POSTGRES_PORT", "5432");
		String pgDb = env("IT_POSTGRES_DB", "aptech4_catalog_it");
		String pgUser = env("IT_POSTGRES_USER", "aptech");
		String pgPassword = env("IT_POSTGRES_PASSWORD", "aptech_dev_change_me");
		String redisHost = env("IT_REDIS_HOST", "localhost");
		String redisPort = env("IT_REDIS_PORT", "6379");

		registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb);
		registry.add("spring.datasource.username", () -> pgUser);
		registry.add("spring.datasource.password", () -> pgPassword);
		registry.add("spring.data.redis.host", () -> redisHost);
		registry.add("spring.data.redis.port", () -> redisPort);
		registry.add("aptech.security.jwt.access-secret", () -> "access-secret-value-32chars-minimum!!");
		registry.add("aptech.security.jwt.refresh-secret", () -> "refresh-secret-value-32chars-minimum!");
		registry.add("aptech.security.jwt.issuer", () -> "aptech4-api");
		registry.add("aptech.security.cors.allowed-origins", () -> "http://localhost:5173");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void publicListsOnlyActiveAndAdminCrudWorks() throws Exception {
		String admin = adminToken();
		String customer = customerToken();
		String unique = UUID.randomUUID().toString().substring(0, 8);
		String name = "Gadgets " + unique;

		mockMvc.perform(get("/api/v1/admin/categories"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
			.andExpect(status().isForbidden());

		MvcResult created = mockMvc.perform(post("/api/v1/admin/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "%s",
						  "description": "Public gadgets"
						}
						""".formatted(name)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value(name))
			.andExpect(jsonPath("$.active").value(true))
			.andExpect(jsonPath("$.slug").isNotEmpty())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andReturn();

		JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
		String id = body.get("id").asText();

		mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id=='%s')].name".formatted(id)).value(name));

		mockMvc.perform(get("/api/v1/categories/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(put("/api/v1/admin/categories/" + id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name": "%s Updated",
						  "description": "Updated desc"
						}
						""".formatted(name)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(name + " Updated"));

		mockMvc.perform(post("/api/v1/admin/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "%s Updated" }
						""".formatted(name)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Category name already exists"));

		mockMvc.perform(post("/api/v1/admin/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "" }
						"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/admin/categories/" + id + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "active": false }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));

		MvcResult publicList = mockMvc.perform(get("/api/v1/categories"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode publicItems = objectMapper.readTree(publicList.getResponse().getContentAsString());
		assertThat(publicItems.isArray()).isTrue();
		for (JsonNode item : publicItems) {
			assertThat(item.get("id").asText()).isNotEqualTo(id);
		}

		mockMvc.perform(get("/api/v1/categories/" + id))
			.andExpect(status().isNotFound());

		MvcResult adminList = mockMvc.perform(get("/api/v1/admin/categories")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isOk())
			.andReturn();
		boolean foundInactive = false;
		for (JsonNode item : objectMapper.readTree(adminList.getResponse().getContentAsString())) {
			if (id.equals(item.get("id").asText())) {
				assertThat(item.get("active").asBoolean()).isFalse();
				foundInactive = true;
			}
		}
		assertThat(foundInactive).isTrue();

		mockMvc.perform(get("/api/v1/categories/" + UUID.randomUUID()))
			.andExpect(status().isNotFound());

		// Soft-disable only — no hard-delete endpoint.
		mockMvc.perform(delete("/api/v1/admin/categories/" + id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
			.andExpect(status().isMethodNotAllowed());

		assertThat(body.has("createdAt")).isFalse();
	}

	@Test
	void openApiIncludesCategoryPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/categories']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/admin/categories']").exists());
	}

	private String adminToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "admin.cat@example.com", List.of("ADMIN"));
	}

	private String customerToken() {
		return jwtService.createAccessToken(UUID.randomUUID(), "cust.cat@example.com", List.of("CUSTOMER"));
	}
}
