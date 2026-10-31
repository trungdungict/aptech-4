package com.aptech.aptech4.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductImageUploadIntegrationTest {

	@TempDir
	static Path uploadRoot;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		IntegrationTestSupport.registerComposeDataSources(registry, "aptech4_catalog_it");
		IntegrationTestSupport.registerSecurityProperties(registry);
		IntegrationTestSupport.registerMockPaymentTestProperties(registry);
		registry.add("aptech.product-image.upload-dir", () -> uploadRoot.resolve("products").toString());
		registry.add("aptech.product-image.max-size-bytes", () -> "2097152");
		registry.add("aptech.seed.enabled", () -> "false");
	}

	@Autowired
	MockMvc mockMvc;

	/** Boot 4 uses Jackson 3 JsonMapper beans; keep a local Jackson 2 mapper for test JSON parsing. */
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	JwtService jwtService;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate transactionTemplate;

	private String adminToken;
	private String customerToken;
	private UUID categoryId;

	@BeforeEach
	void setUpUsers() {
		transactionTemplate.executeWithoutResult(status -> {
			Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
			Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();

			UserAccount admin = userAccountRepository.findByEmailWithRoles("img.admin@example.com").orElseGet(() -> {
				UserAccount created = UserAccount.createNew(
						"img.admin@example.com",
						passwordEncoder.encode("Password1!"),
						"Img Admin"
				);
				created.setRoles(Set.of(adminRole));
				return userAccountRepository.saveAndFlush(created);
			});
			adminToken = jwtService.createAccessToken(admin.getId(), admin.getEmail(), List.of("ADMIN"));

			UserAccount customer = userAccountRepository.findByEmailWithRoles("img.customer@example.com").orElseGet(() -> {
				UserAccount created = UserAccount.createNew(
						"img.customer@example.com",
						passwordEncoder.encode("Password1!"),
						"Img Customer"
				);
				created.setRoles(Set.of(customerRole));
				return userAccountRepository.saveAndFlush(created);
			});
			customerToken = jwtService.createAccessToken(customer.getId(), customer.getEmail(), List.of("CUSTOMER"));

			Category category = categoryRepository.findBySlug("img-upload-cat").orElseGet(() ->
					categoryRepository.saveAndFlush(
							Category.createNew("Image Upload Cat", "img-upload-cat", "test", null)
					)
			);
			categoryId = category.getId();
		});
	}

	@Test
	void adminCanUploadValidPngAndServeIt() throws Exception {
		byte[] png = tinyPng();
		MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

		MvcResult result = mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").isString())
				.andExpect(jsonPath("$.contentType").value("image/png"))
				.andExpect(jsonPath("$.sizeBytes").value(png.length))
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		String imageUrl = body.get("imageUrl").asText();
		assertThat(imageUrl).startsWith("/uploads/products/").endsWith(".png");
		assertThat(imageUrl).doesNotContain("..");
		// Public URL path (starts with /) — must not embed the host filesystem upload root.
		assertThat(imageUrl).doesNotContain(uploadRoot.toAbsolutePath().normalize().toString());
		assertThat(imageUrl).doesNotContain("\\");

		Path stored = uploadRoot.resolve("products").resolve(Path.of(imageUrl).getFileName().toString());
		assertThat(Files.exists(stored)).isTrue();
		assertThat(Files.size(stored)).isEqualTo(png.length);

		mockMvc.perform(get(imageUrl))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		String sku = "IMG-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult created = mockMvc.perform(post("/api/v1/admin/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sku": "%s",
								  "name": "Imaged Product",
								  "unitPrice": 1000,
								  "currency": "VND",
								  "categoryId": "%s",
								  "imageUrl": "%s"
								}
								""".formatted(sku, categoryId, imageUrl)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageUrl").value(imageUrl))
				.andReturn();

		UUID productId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
		Product persisted = productRepository.findById(productId).orElseThrow();
		assertThat(persisted.getImageUrl()).isEqualTo(imageUrl);
		assertThat(persisted.getImageUrl()).doesNotContain("base64");
		assertThat(persisted.getImageUrl()).doesNotStartWith("data:");
	}

	@Test
	void productUpdateAcceptsUploadedImagePath() throws Exception {
		byte[] png = tinyPng();
		String imageUrl = uploadAsAdmin(png, "upd.png");

		String sku = "IMG-U-" + UUID.randomUUID().toString().substring(0, 8);
		MvcResult created = mockMvc.perform(post("/api/v1/admin/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sku": "%s",
								  "name": "Before Image",
								  "unitPrice": 500,
								  "currency": "VND",
								  "categoryId": "%s"
								}
								""".formatted(sku, categoryId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageUrl").value(Matchers.nullValue()))
				.andReturn();

		String productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(put("/api/v1/admin/products/" + productId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sku": "%s",
								  "name": "After Image",
								  "unitPrice": 500,
								  "currency": "VND",
								  "categoryId": "%s",
								  "imageUrl": "%s"
								}
								""".formatted(sku, categoryId, imageUrl)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").value(imageUrl));

		Product persisted = productRepository.findById(UUID.fromString(productId)).orElseThrow();
		assertThat(persisted.getImageUrl()).isEqualTo(imageUrl);
	}

	@Test
	void anonymousCustomerAndInvalidJwtAreRejected() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", tinyPng());
		mockMvc.perform(multipart("/api/v1/admin/uploads/images").file(file))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void wrongMultipartFieldNameIsRejected() throws Exception {
		MockMultipartFile wrongField = new MockMultipartFile("image", "a.png", "image/png", tinyPng());
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(wrongField)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest());
	}

	@Test
	void emptyAndMissingFileRejected() throws Exception {
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest());
	}

	@Test
	void exactlyTwoMegabytesRejected() throws Exception {
		byte[] exact = new byte[2 * 1024 * 1024];
		System.arraycopy(tinyPng(), 0, exact, 0, tinyPng().length);
		long before = countStoredFiles();
		MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", exact);
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(Matchers.containsString("smaller than")));
		assertThat(countStoredFiles()).isEqualTo(before);
	}

	@Test
	void oversizedRejectedWithoutLeavingFile() throws Exception {
		byte[] over = new byte[2 * 1024 * 1024 + 64];
		System.arraycopy(tinyPng(), 0, over, 0, tinyPng().length);
		long before = countStoredFiles();
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(new MockMultipartFile("file", "huge.png", "image/png", over))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest());
		assertThat(countStoredFiles()).isEqualTo(before);
	}

	@Test
	void spoofedExtensionRejectedWithoutLeavingFile() throws Exception {
		long before = countStoredFiles();
		byte[] notImage = "not-an-image".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "evil.png", "image/png", notImage);
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(file)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest());
		assertThat(countStoredFiles()).isEqualTo(before);
	}

	@Test
	void underLimitJpegAccepted() throws Exception {
		byte[] jpeg = tinyJpeg();
		assertThat(jpeg.length).isLessThan(2 * 1024 * 1024);
		mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(new MockMultipartFile("file", "ok.jpg", "image/jpeg", jpeg))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").value(Matchers.endsWith(".jpg")))
				.andExpect(jsonPath("$.contentType").value("image/jpeg"))
				.andExpect(jsonPath("$.sizeBytes").value(jpeg.length));
	}

	@Test
	void createWithoutImageUrlRemainsCompatible() throws Exception {
		String sku = "IMG-C-" + UUID.randomUUID().toString().substring(0, 8);
		mockMvc.perform(post("/api/v1/admin/products")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "sku": "%s",
								  "name": "No Image Product",
								  "unitPrice": 100,
								  "currency": "VND",
								  "categoryId": "%s"
								}
								""".formatted(sku, categoryId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.imageUrl").value(Matchers.nullValue()));
	}

	private String uploadAsAdmin(byte[] bytes, String filename) throws Exception {
		MvcResult result = mockMvc.perform(multipart("/api/v1/admin/uploads/images")
						.file(new MockMultipartFile("file", filename, "image/png", bytes))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("imageUrl").asText();
	}

	private long countStoredFiles() throws Exception {
		Path dir = uploadRoot.resolve("products");
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(Files::isRegularFile)
					.filter(p -> !p.getFileName().toString().endsWith(".tmp"))
					.count();
		}
	}

	private static byte[] tinyPng() throws Exception {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.RED.getRGB());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] tinyJpeg() throws Exception {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.BLUE.getRGB());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}
}
