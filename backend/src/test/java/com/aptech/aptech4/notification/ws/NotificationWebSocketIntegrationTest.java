package com.aptech.aptech4.notification.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

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
import com.aptech.aptech4.notification.api.NotificationResponse;
import com.aptech.aptech4.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class NotificationWebSocketIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		IntegrationTestSupport.registerComposeDataSources(registry, "aptech4_catalog_it");
		IntegrationTestSupport.registerSecurityProperties(registry);
		IntegrationTestSupport.registerMockPaymentTestProperties(registry);
	}

	@LocalServerPort
	int port;

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
	PasswordEncoder passwordEncoder;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	private WebSocketStompClient stompClient;

	@BeforeEach
	void setUpClient() {
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setObjectMapper(objectMapper);
		stompClient.setMessageConverter(converter);
	}

	@Test
	void connectedFrameNegotiatesNonZeroHeartbeats() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("ws.hb." + suffix + "@example.com", "Ws Hb");

		CompletableFuture<StompHeaders> connectedHeaders = new CompletableFuture<>();
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		StompSession session = stompClient
				.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
					@Override
					public void afterConnected(StompSession session, StompHeaders connectedHeadersFrame) {
						connectedHeaders.complete(connectedHeadersFrame);
					}
				})
				.get(5, TimeUnit.SECONDS);

		StompHeaders headers = connectedHeaders.get(5, TimeUnit.SECONDS);
		String heartbeat = headers.getFirst("heart-beat");
		assertThat(heartbeat).isNotNull().isNotEqualTo("0,0");
		String[] parts = heartbeat.split(",");
		assertThat(Long.parseLong(parts[0].trim())).isGreaterThan(0);
		assertThat(Long.parseLong(parts[1].trim())).isGreaterThan(0);
		session.disconnect();
	}

	@Test
	void connectRequiresValidAccessJwt() {
		assertThatThrownBy(() -> connect("not-a-jwt"))
			.isInstanceOf(Exception.class);

		String expired = Jwts.builder()
			.issuer("aptech4-api")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(Instant.now().minusSeconds(120)))
			.expiration(Date.from(Instant.now().minusSeconds(60)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(Keys.hmacShaKeyFor("access-secret-value-32chars-minimum!!".getBytes(StandardCharsets.UTF_8)))
			.compact();

		assertThatThrownBy(() -> connect(expired))
			.isInstanceOf(Exception.class);
	}

	@Test
	void unauthorizedSubscriptionIsRejected() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String token = register("ws.sub." + suffix + "@example.com", "Ws Sub");

		CompletableFuture<Throwable> failure = new CompletableFuture<>();
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
			@Override
			public void handleException(
					StompSession session,
					StompCommand command,
					StompHeaders headers,
					byte[] payload,
					Throwable exception
			) {
				failure.complete(exception);
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				failure.complete(exception);
			}
		};
		StompSession session = stompClient
				.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, handler)
				.get(5, TimeUnit.SECONDS);

		session.subscribe("/topic/notifications", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return byte[].class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
			}
		});

		Throwable problem = failure.get(5, TimeUnit.SECONDS);
		assertThat(problem).isNotNull();
		if (session.isConnected()) {
			session.disconnect();
		}
	}

	@Test
	void adminReceivesPaidPushAndCustomerReceivesStatusPushWithIsolation() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("ws.admin." + suffix + "@example.com");
		String customerToken = register("ws.cust." + suffix + "@example.com", "Ws Customer");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();
		String otherToken = register("ws.other." + suffix + "@example.com", "Ws Other");

		UUID addressId = seedAddress(customerId, "Ws-" + suffix);
		UUID productId = seedProduct("WS-" + suffix, new BigDecimal("55.00"), 10);

		CompletableFuture<NotificationResponse> adminPaid = new CompletableFuture<>();
		CompletableFuture<NotificationResponse> customerPaid = new CompletableFuture<>();
		CompletableFuture<NotificationResponse> customerStatus = new CompletableFuture<>();
		CompletableFuture<NotificationResponse> otherLeak = new CompletableFuture<>();

		StompSession adminSession = connectAndSubscribe(admin.token(), n -> {
			if ("ORDER_PAID".equals(n.type())) {
				adminPaid.complete(n);
			}
		});
		StompSession customerSession = connectAndSubscribe(customerToken, n -> {
			if ("ORDER_PAID".equals(n.type())) {
				customerPaid.complete(n);
			}
			if ("ORDER_STATUS_CHANGED".equals(n.type())) {
				customerStatus.complete(n);
			}
		});
		StompSession otherSession = connectAndSubscribe(otherToken, otherLeak::complete);

		addToCart(customerToken, productId, 1);
		MvcResult checkout = mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "ws-paid-%s", "addressId": "%s" }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("PAID"))
			.andReturn();
		String orderId = objectMapper.readTree(checkout.getResponse().getContentAsString()).get("orderId").asText();

		long afterCheckoutHttp = System.nanoTime();
		NotificationResponse paidPush = adminPaid.get(2, TimeUnit.SECONDS);
		long adminPushMs = (System.nanoTime() - afterCheckoutHttp) / 1_000_000L;
		assertThat(adminPushMs)
				.as("admin ORDER_PAID push should be near-realtime after commit, not ~sendTimeLimit")
				.isLessThan(2_000L);
		assertThat(paidPush.type()).isEqualTo("ORDER_PAID");
		assertThat(paidPush.orderId().toString()).isEqualTo(orderId);
		assertThat(paidPush.unread()).isTrue();
		assertThat(customerPaid.get(2, TimeUnit.SECONDS).type()).isEqualTo("ORDER_PAID");

		assertThat(otherLeak.isDone()).isFalse();

		mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "status": "PROCESSING" }
						"""))
			.andExpect(status().isOk());

		long afterStatusHttp = System.nanoTime();
		NotificationResponse statusPush = customerStatus.get(2, TimeUnit.SECONDS);
		long statusPushMs = (System.nanoTime() - afterStatusHttp) / 1_000_000L;
		assertThat(statusPushMs)
				.as("customer status push should be near-realtime after commit")
				.isLessThan(2_000L);
		assertThat(statusPush.type()).isEqualTo("ORDER_STATUS_CHANGED");
		assertThat(statusPush.orderId().toString()).isEqualTo(orderId);
		assertThat(statusPush.message()).containsIgnoringCase("processing");

		assertThat(otherLeak.isDone()).isFalse();

		adminSession.disconnect();
		customerSession.disconnect();
		otherSession.disconnect();
	}

	@Test
	void pushHappensOnlyAfterCommit() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AdminFixture admin = seedAdmin("ws.rollback.admin." + suffix + "@example.com");
		CompletableFuture<NotificationResponse> adminPaid = new CompletableFuture<>();
		StompSession adminSession = connectAndSubscribe(admin.token(), adminPaid::complete);

		// Simulate rollback: persist path is only invoked after successful checkout commit.
		// Force a failed checkout — no push should arrive.
		String customerToken = register("ws.rollback." + suffix + "@example.com", "Rollback");
		UUID customerId = jwtService.parseAccessToken(customerToken).userId();
		UUID addressId = seedAddress(customerId, "Rb-" + suffix);
		UUID productId = seedProduct("WSR-" + suffix, new BigDecimal("30.00"), 5);
		addToCart(customerToken, productId, 1);

		mockMvc.perform(post("/api/v1/checkout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "idempotencyKey": "ws-fail-%s", "addressId": "%s", "simulateFailure": true }
						""".formatted(suffix, addressId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orderStatus").value("FAILED"));

		Thread.sleep(800);
		assertThat(adminPaid.isDone()).isFalse();

		adminSession.disconnect();
	}

	private StompSession connectAndSubscribe(
			String accessToken,
			java.util.function.Consumer<NotificationResponse> onMessage
	) throws Exception {
		StompSession session = connect(accessToken);
		session.subscribe(NotificationDestinations.USER_QUEUE, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return NotificationResponse.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				onMessage.accept((NotificationResponse) payload);
			}
		});
		return session;
	}

	private StompSession connect(String accessToken) throws Exception {
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
			@Override
			public void handleException(
					StompSession session,
					StompCommand command,
					StompHeaders headers,
					byte[] payload,
					Throwable exception
			) {
				failure.set(exception);
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				failure.set(exception);
			}
		};
		CompletableFuture<StompSession> future = stompClient
				.connectAsync(wsUrl(), new WebSocketHttpHeaders(), connectHeaders, handler)
				.toCompletableFuture();
		try {
			return future.get(5, TimeUnit.SECONDS);
		}
		catch (Exception ex) {
			if (failure.get() != null) {
				throw new IllegalStateException("STOMP connect failed", failure.get());
			}
			throw ex;
		}
	}

	private String wsUrl() {
		return "ws://localhost:" + port + "/ws";
	}

	private AdminFixture seedAdmin(String email) {
		return transactionTemplate.execute(status -> {
			Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
			UserAccount account = UserAccount.createNew(
					email, passwordEncoder.encode("Password1!"), "Ws Admin"
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
		String slug = "ws-cat-" + sku.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		return transactionTemplate.execute(status -> {
			Category category = categoryRepository.saveAndFlush(Category.createNew(
					"Ws Cat " + sku, slug, null, null
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
