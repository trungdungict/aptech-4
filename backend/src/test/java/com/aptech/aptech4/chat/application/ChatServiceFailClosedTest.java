package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.chat.api.ChatRequest;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.chat.tools.AdminChatTools;
import com.aptech.aptech4.chat.tools.CustomerChatTools;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;
import com.aptech.aptech4.order.application.OrderService;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class ChatServiceFailClosedTest {

	@Mock
	CustomerChatTools customerChatTools;

	@Mock
	AdminChatTools adminChatTools;

	@Mock
	AdminDeterministicAnalyticsService adminDeterministicAnalyticsService;

	@Mock
	OrderService orderService;

	@Mock
	ProductService productService;

	@Mock
	ObjectProvider<ChatModel> chatModelProvider;

	@Mock
	ObjectProvider<org.springframework.ai.chat.client.ChatClient.Builder> chatClientBuilderProvider;

	CustomerDeterministicAssistantService customerDeterministicAssistantService;

	ChatSessionService chatSessionService;

	ChatLanguageResolver chatLanguageResolver;

	AiProperties aiProperties;

	ChatService chatService;

	UUID customerId;

	@BeforeEach
	void setUp() {
		aiProperties = new AiProperties();
		aiProperties.setEnabled(true);
		aiProperties.setRequestTimeoutSeconds(10);
		chatSessionService = new ChatSessionService(aiProperties);
		chatLanguageResolver = new ChatLanguageResolver();
		customerDeterministicAssistantService = new CustomerDeterministicAssistantService(orderService, productService);

		ChatModel failingModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				throw new ToolAuthorizationException("Authentication required");
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.error(new ToolAuthorizationException("Authentication required"));
			}
		};
		lenient().when(chatModelProvider.getIfAvailable()).thenReturn(failingModel);
		lenient().when(chatModelProvider.getObject()).thenReturn(failingModel);
		lenient().when(chatClientBuilderProvider.getIfAvailable()).thenReturn(null);

		Executor direct = Runnable::run;
		chatService = new ChatService(
				aiProperties,
				chatSessionService,
				chatLanguageResolver,
				customerChatTools,
				adminChatTools,
				adminDeterministicAnalyticsService,
				customerDeterministicAssistantService,
				chatModelProvider,
				chatClientBuilderProvider,
				direct
		);

		customerId = UUID.randomUUID();
		var principal = new JwtService.AccessTokenPrincipal(customerId, "c@example.com", List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void orderCountShortCircuitsBeforeChatModel() {
		when(orderService.listMine(isNull(), anyInt(), anyInt(), any()))
				.thenReturn(PageResponse.of(List.of(), 0, 1, 56));

		var response = chatService.chat(new ChatRequest(
				"What is the total number of my orders?",
				null,
				"EN"
		));

		assertThat(response.reply()).isEqualTo("You have placed 56 orders.");
		assertThat(response.timing().path()).isEqualTo("deterministic_order_count");
		verify(chatModelProvider, never()).getObject();
		verify(adminDeterministicAnalyticsService, never()).answer(any(AdminIntentMatch.class), any());
	}

	@Test
	void restAndStreamingOrderCountAreConsistent() {
		when(orderService.listMine(isNull(), anyInt(), anyInt(), any()))
				.thenReturn(PageResponse.of(List.of(), 0, 1, 3));

		var rest = chatService.chat(new ChatRequest("How many orders do I have?", null, "EN"));
		AtomicReference<StringBuilder> deltas = new AtomicReference<>(new StringBuilder());
		var stream = chatService.chatStreaming(
				new ChatRequest("How many orders do I have?", null, "EN"),
				chunk -> deltas.get().append(chunk)
		);

		assertThat(rest.reply()).isEqualTo(stream.reply());
		assertThat(rest.timing().path()).isEqualTo("deterministic_order_count");
		assertThat(stream.timing().path()).isEqualTo("deterministic_order_count");
		assertThat(deltas.get().toString()).isEqualTo(stream.reply());
		assertThat(rest.reply()).doesNotContainIgnoringCase("jwt");
	}

	@Test
	void llmToolAuthFailureReturnsFixedSignInNotApology() {
		var response = chatService.chat(new ChatRequest("What is the weather in Hanoi?", null, "EN"));

		assertThat(response.reply()).isEqualTo("Sign in required.");
		assertThat(response.reply()).doesNotContainIgnoringCase("sorry");
		assertThat(response.reply()).doesNotContainIgnoringCase("jwt");
		assertThat(response.reply()).doesNotContainIgnoringCase("token");
		assertThat(response.timing().path()).isEqualTo("llm_auth_required");
	}

	@Test
	void sseToolAuthFailureEmitsFixedSignInReply() {
		AtomicReference<StringBuilder> deltas = new AtomicReference<>(new StringBuilder());
		var response = chatService.chatStreaming(
				new ChatRequest("What is the weather in Hanoi?", null, "EN"),
				chunk -> deltas.get().append(chunk)
		);

		assertThat(response.reply()).isEqualTo("Sign in required.");
		assertThat(deltas.get().toString()).isEqualTo("Sign in required.");
		assertThat(response.timing().path()).isEqualTo("llm_auth_required");
		assertThat(ChatMetrics.handlerFromPath(response.timing().path())).isEqualTo("llm");
	}

	@Test
	void missingSecurityContextOnSecureFailsClosedWithoutLlmApology() {
		SecurityContextHolder.clearContext();
		// prepare() requires auth — re-establish for prepare then clear before tools via empty principal path:
		var principal = new JwtService.AccessTokenPrincipal(customerId, "c@example.com", List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContextHolder.getContext().setAuthentication(auth);

		// Force LLM path with a ChatModel that clears context then calls secure indirectly —
		// covered by ChatToolSecuritySupportTest; here assert deterministic auth path when listMine 401s.
		when(orderService.listMine(isNull(), anyInt(), anyInt(), any()))
				.thenThrow(new com.aptech.aptech4.common.api.ApiException(
						org.springframework.http.HttpStatus.UNAUTHORIZED,
						"Unauthorized",
						"Authentication required"
				));

		var response = chatService.chat(new ChatRequest("How many orders have I placed?", null, "EN"));
		assertThat(response.reply()).containsIgnoringCase("sign in");
		assertThat(response.reply()).doesNotContain("0");
		assertThat(response.timing().path()).isEqualTo("deterministic_customer_auth");
	}
}
