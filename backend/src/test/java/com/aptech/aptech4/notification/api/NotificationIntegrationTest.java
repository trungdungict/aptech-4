package com.aptech.aptech4.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.catalog.domain.Category;
import com.aptech.aptech4.catalog.domain.Inventory;
import com.aptech.aptech4.catalog.domain.Product;
import com.aptech.aptech4.catalog.persistence.CategoryRepository;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.catalog.persistence.ProductRepository;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.notification.domain.NotificationType;
import com.aptech.aptech4.notification.persistence.AppNotificationRepository;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.domain.ShopOrder;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

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
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
		registry.add("spring.data.redis.host", () -> redisHost);
		registry.add("spring.data.redis.port", () -> redisPort);
		registry.add("aptech.security.jwt.access-secret", () -> "access-secret-value-32chars-minimum!!");
		registry.add("aptech.security.jwt.refresh-secret", () -> "refresh-secret-value-32chars-minimum!");
		registry.add("aptech.security.jwt.issuer", () -> "aptech4-api");
		registry.add("aptech.security.cors.allowed-origins", () -> "http://localhost:5173");
		registry.add("aptech.payment.mock.allow-client-force-failure", () -> "true");
		registry.add("aptech.payment.mock.default-outcome", () -> "SUCCEEDED");
	}

	private static String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return (value == null || value.isBlank()) ? defaultValue : value;
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	InventoryRepository inventoryRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	UserAccountRepository userAccountRepository;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	ShopOrderRepository shopOrderRepository;

	@Autowired
	AppNotificationRepository notificationRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void paidCheckoutCreatesNotificationsAndApiIsRecipientScoped() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("notif.admin." + suffix + "@example.com");
		String customerToken = register("notif.cust." + suffix + "@example.com", "Notif Customer");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();
		UUID addressId = seedAddress(customerId, "Home-" + suffix);
		UUID productId = seedProduct("NTF-" + suffix, new BigDecimal("50.00"), 10);

		mockMvc.perform(get("/api/v1/notifications"))
			.andExpect(status().isUnauthorized());

		addToCart(customerToken, productId, 1);
		MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "notif-paid-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("PAID"))
			.andReturn();
		UUID orderId = UUID.fromString(
				objectMapper.readTree(checkout.getResponse().getContentAsString()).get("orderId").asText()
		);

		// Idempotent replay must not duplicate notifications
		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "notif-paid-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("PAID"));

		assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(customerId)).isEqualTo(1);
		assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.userId())).isEqualTo(1);
		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				customerId, "ORDER_PAID:" + orderId + ":customer"
		)).isTrue();

		mockMvc.perform(get("/api/v1/notifications/unread-count")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(1));

		mockMvc.perform(get("/api/v1/notifications")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("ORDER_PAID"))
			.andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
			.andExpect(jsonPath("$.items[0].unread").value(true));

		mockMvc.perform(get("/api/v1/notifications")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].type").value("ORDER_PAID"))
			.andExpect(jsonPath("$.items[0].title").value("New paid order"));

		String customerNotifId = objectMapper.readTree(
				mockMvc.perform(get("/api/v1/notifications")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString()
		).get("items").get(0).get("id").asText();

		// Other user cannot mark / see this notification
		mockMvc.perform(patch("/api/v1/notifications/" + customerNotifId + "/read")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/v1/notifications/" + customerNotifId + "/read")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unread").value(false));

		mockMvc.perform(get("/api/v1/notifications/unread-count")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(0));

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/notifications']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/notifications/unread-count']").exists());
	}

	@Test
	void failedCheckoutDoesNotCreateNotifications() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("notif.fail.admin." + suffix + "@example.com");
		String customerToken = register("notif.fail." + suffix + "@example.com", "Fail Customer");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();
		UUID addressId = seedAddress(customerId, "Fail-" + suffix);
		UUID productId = seedProduct("NTF-F-" + suffix, new BigDecimal("40.00"), 5);

		long customerBefore = notificationRepository.countByRecipient_IdAndReadAtIsNull(customerId);
		long adminBefore = notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.userId());

		addToCart(customerToken, productId, 1);
		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "notif-fail-%s", "addressId": "%s", "simulateFailure": true }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("FAILED"));

		assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(customerId)).isEqualTo(customerBefore);
		assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(admin.userId())).isEqualTo(adminBefore);
	}

	@Test
	void statusTransitionsAndCancelCreateNotificationsWithoutDuplicates() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("notif.st.admin." + suffix + "@example.com");
		String customerToken = register("notif.st." + suffix + "@example.com", "Status Customer");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();
		UUID addressId = seedAddress(customerId, "St-" + suffix);
		UUID productId = seedProduct("NTF-S-" + suffix, new BigDecimal("60.00"), 8);

		addToCart(customerToken, productId, 1);
		MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "notif-st-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID orderId = UUID.fromString(
				objectMapper.readTree(checkout.getResponse().getContentAsString()).get("orderId").asText()
		);

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "PROCESSING" }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PROCESSING"));

		// Unchanged status → no duplicate
		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "PROCESSING" }
						"""))
			.andExpect(status().isOk());

		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				customerId, "ORDER_STATUS:" + orderId + ":PROCESSING"
		)).isTrue();
		long processingCount = countTypeForRecipient(customerId, NotificationType.ORDER_STATUS_CHANGED);
		assertThat(processingCount).isEqualTo(1);

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "CANCELLED" }
						"""))
			.andExpect(status().isOk());

		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				customerId, "ORDER_CANCELLED:" + orderId + ":customer"
		)).isTrue();
		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				admin.userId(), "ORDER_CANCELLED:" + orderId + ":admin:" + admin.userId()
		)).isTrue();

		// Rejected transition → no new notification
		long beforeReject = notificationRepository.countByRecipient_IdAndReadAtIsNull(customerId);
		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "SHIPPED" }
						"""))
			.andExpect(status().isConflict());
		assertThat(notificationRepository.countByRecipient_IdAndReadAtIsNull(customerId)).isEqualTo(beforeReject);

		// Mark-all-as-read
		mockMvc.perform(patch("/api/v1/notifications/read-all")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.unreadCount").value(0));
	}

	@Test
	void customerCancelCreatesNotifications() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("notif.cancel.admin." + suffix + "@example.com");
		String customerToken = register("notif.cancel." + suffix + "@example.com", "Cancel Customer");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();

		UUID orderId = transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.findById(customerId).orElseThrow();
			Address address = addressRepository.saveAndFlush(Address.createNew(
					user, "R", "L1", null, "Hanoi", null, "100000", "VN", "0900000000", true
			));
			ShopOrder order = ShopOrder.createPending(user, address);
			order.setSubtotalAmount(new BigDecimal("10.00"));
			order.setTotalAmount(new BigDecimal("10.00"));
			order.setCurrency("VND");
			return shopOrderRepository.saveAndFlush(order).getId();
		});

		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));

		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				customerId, "ORDER_CANCELLED:" + orderId + ":customer"
		)).isTrue();
		assertThat(notificationRepository.existsByRecipient_IdAndDedupeKey(
				admin.userId(), "ORDER_CANCELLED:" + orderId + ":admin:" + admin.userId()
		)).isTrue();

		// Second cancel rejected — no duplicate
		mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
			.andExpect(status().isConflict());
		assertThat(countTypeForRecipient(customerId, NotificationType.ORDER_CANCELLED)).isEqualTo(1);
	}

	private long countTypeForRecipient(UUID recipientId, NotificationType type) {
		return transactionTemplate.execute(status ->
				notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(
								recipientId, org.springframework.data.domain.PageRequest.of(0, 100)
						)
						.getContent()
						.stream()
						.filter(n -> n.getType() == type)
						.count()
		);
	}

	private AdminFixture seedAdmin(String email) {
		return transactionTemplate.execute(status -> {
			Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
			UserAccount account = UserAccount.createNew(
					email, passwordEncoder.encode("Password1!"), "Notif Admin"
			);
			account.setRoles(Set.of(adminRole));
			UserAccount saved = userAccountRepository.saveAndFlush(account);
			String token = jwtService.createAccessToken(saved.getId(), email, List.of("ADMIN"));
			return new AdminFixture(saved.getId(), token);
		});
	}

	private void addToCart(String token, UUID productId, int qty) throws Exception {
		mockMvc.perform(post("/api/v1/cart/items")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "productId": "%s", "quantity": %d }
						""".formatted(productId, qty)))
			.andExpect(status().isOk());
	}

	private UUID seedProduct(String sku, BigDecimal price, int onHand) {
		String slug = "ntf-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Ntf Cat " + sku, slug, null, null
			));
			Product product = productRepository.saveAndFlush(Product.createNew(
					category, sku, "Product " + sku, null, price, "VND", true
			));
			inventoryRepository.saveAndFlush(Inventory.forProduct(product, onHand));
			return product.getId();
		});
	}

	private UUID seedAddress(UUID userId, String label) {
		return transactionTemplate.execute(status -> {
			UserAccount user = userAccountRepository.findById(userId).orElseThrow();
			Address address = Address.createNew(
					user, "Recipient " + label, "Line 1", null, "Hanoi", null, "100000", "VN", "0900000000", true
			);
			address.setLabel(label);
			return addressRepository.saveAndFlush(address).getId();
		});
	}

	private String register(String email, String fullName) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "Password1!",
						  "fullName": "%s"
						}
						""".formatted(email, fullName)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private record AdminFixture(UUID userId, String token) {
	}
}
