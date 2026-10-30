package com.aptech.aptech4.chat.api;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aptech.aptech4.chat.application.ChatMetrics;
import com.aptech.aptech4.chat.application.ChatService;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "AI Chat", description = "Local Ollama assistant (ADR-019). Read-only tools; AI optional.")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("isAuthenticated()")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

	private final ChatService chatService;
	private final AiProperties aiProperties;
	private final Executor chatInferenceExecutor;
	private final ChatMetrics chatMetrics;

	public ChatController(
			ChatService chatService,
			AiProperties aiProperties,
			@Qualifier("chatInferenceExecutor") Executor chatInferenceExecutor,
			ChatMetrics chatMetrics
	) {
		this.chatService = chatService;
		this.aiProperties = aiProperties;
		this.chatInferenceExecutor = chatInferenceExecutor;
		this.chatMetrics = chatMetrics;
	}

	@GetMapping("/health")
	@Operation(summary = "AI feature health (does not require Ollama when disabled)")
	public ChatHealthResponse health() {
		boolean enabled = aiProperties.isEnabled();
		boolean ready = chatService.isEnabled();
		String status;
		String detail;
		if (!enabled) {
			status = "DISABLED";
			detail = "Set APTECH_AI_ENABLED=true and profile ai / spring.ai.model.chat=ollama.";
		}
		else if (!ready) {
			status = "UNAVAILABLE";
			detail = "Enabled but ChatModel bean missing — check Ollama URL and model.";
		}
		else {
			status = "READY";
			detail = "Chat model configured.";
		}
		return new ChatHealthResponse(enabled, ready, aiProperties.getModel(), status, detail);
	}

	@PostMapping("/messages")
	@Operation(summary = "Non-streaming chat completion")
	public ChatResponse message(@Valid @RequestBody ChatRequest request) {
		long started = System.nanoTime();
		try {
			ChatResponse response = chatService.chat(request);
			String handler = ChatMetrics.handlerFromPath(
					response.timing() == null ? null : response.timing().path()
			);
			long ms = response.timing() != null && response.timing().totalMs() != null
					? response.timing().totalMs()
					: Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
			chatMetrics.recordRequest("rest", handler, "success", ms);
			return response;
		}
		catch (RuntimeException ex) {
			chatMetrics.recordRequest(
					"rest",
					"unknown",
					"error",
					Math.max(0L, (System.nanoTime() - started) / 1_000_000L)
			);
			throw ex;
		}
	}

	@PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "SSE streaming chat completion")
	public SseEmitter stream(@Valid @RequestBody ChatRequest request, HttpServletResponse response) {
		// Privacy-safe correlation id (never a JWT or message body).
		String streamId = UUID.randomUUID().toString().substring(0, 8);
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("X-Chat-Stream-Id", streamId);

		long timeoutMs = Math.max(30_000L, aiProperties.getRequestTimeoutSeconds() * 1000L);
		SseEmitter emitter = new SseEmitter(timeoutMs);
		AtomicBoolean terminalSent = new AtomicBoolean(false);
		AtomicBoolean firstDelta = new AtomicBoolean(false);
		int[] deltaCount = { 0 };

		emitter.onCompletion(() -> log.info(
				"chat_sse streamId={} stage=completion deltas={}",
				streamId,
				deltaCount[0]
		));
		emitter.onTimeout(() -> {
			log.warn("chat_sse streamId={} stage=timeout deltas={}", streamId, deltaCount[0]);
			sendTerminal(emitter, terminalSent, streamId, "error", "Assistant stream timed out");
		});
		emitter.onError(ex -> log.warn(
				"chat_sse streamId={} stage=emitter_error deltas={} type={}",
				streamId,
				deltaCount[0],
				ex == null ? "null" : ex.getClass().getSimpleName()
		));

		log.info("chat_sse streamId={} stage=started", streamId);
		long streamStartedNanos = System.nanoTime();
		chatInferenceExecutor.execute(() -> {
			try {
				ChatResponse completed = chatService.chatStreaming(request, chunk -> {
					if (chunk == null || chunk.isEmpty() || terminalSent.get()) {
						return;
					}
					try {
						// Explicit JSON string (quotes + escapes). Plain `data: <chunk>` loses a
						// leading space because SSE clients strip one optional space after `data:`
						// (LLM / chunk boundaries often start with ` `). MediaType.APPLICATION_JSON
						// alone is not enough for String — converters may write the raw value.
						emitter.send(SseEmitter.event()
								.name("delta")
								.data(OBJECT_MAPPER.writeValueAsString(chunk)));
						if (firstDelta.compareAndSet(false, true)) {
							log.info("chat_sse streamId={} stage=first_delta", streamId);
						}
						deltaCount[0]++;
					}
					catch (IOException ex) {
						throw new ClientGoneException(ex);
					}
				});
				if (terminalSent.get()) {
					return;
				}
				String doneJson = OBJECT_MAPPER.writeValueAsString(completed);
				sendTerminal(emitter, terminalSent, streamId, "done", doneJson);
				String handler = ChatMetrics.handlerFromPath(
						completed.timing() == null ? null : completed.timing().path()
				);
				long ms = completed.timing() != null && completed.timing().totalMs() != null
						? completed.timing().totalMs()
						: Math.max(0L, (System.nanoTime() - streamStartedNanos) / 1_000_000L);
				chatMetrics.recordRequest("stream", handler, "success", ms);
				log.info(
						"chat_sse streamId={} stage=done deltas={} path={}",
						streamId,
						deltaCount[0],
						completed.timing() == null ? "?" : completed.timing().path()
				);
			}
			catch (ClientGoneException ex) {
				log.info("chat_sse streamId={} stage=client_gone deltas={}", streamId, deltaCount[0]);
				chatMetrics.recordSseDisconnect();
				chatMetrics.recordRequest(
						"stream",
						"unknown",
						"interrupted",
						Math.max(0L, (System.nanoTime() - streamStartedNanos) / 1_000_000L)
				);
				safeComplete(emitter, terminalSent);
			}
			catch (ApiException ex) {
				log.warn(
						"chat_sse streamId={} stage=api_error deltas={} status={}",
						streamId,
						deltaCount[0],
						ex.getStatus()
				);
				chatMetrics.recordSseError();
				chatMetrics.recordRequest(
						"stream",
						"unknown",
						"error",
						Math.max(0L, (System.nanoTime() - streamStartedNanos) / 1_000_000L)
				);
				sendTerminal(emitter, terminalSent, streamId, "error", safeErrorMessage(ex));
			}
			catch (Exception ex) {
				log.error(
						"chat_sse streamId={} stage=failure deltas={} type={}",
						streamId,
						deltaCount[0],
						ex.getClass().getSimpleName(),
						ex
				);
				chatMetrics.recordSseError();
				chatMetrics.recordRequest(
						"stream",
						"unknown",
						"error",
						Math.max(0L, (System.nanoTime() - streamStartedNanos) / 1_000_000L)
				);
				sendTerminal(emitter, terminalSent, streamId, "error", "Assistant stream failed");
			}
		});
		return emitter;
	}

	@GetMapping("/suggested-prompts")
	@Operation(summary = "Default suggested prompts for the current role/language")
	public ResponseEntity<?> suggestedPrompts() {
		boolean admin = org.springframework.security.core.context.SecurityContextHolder.getContext()
				.getAuthentication()
				.getAuthorities()
				.stream()
				.anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
		var prompts = admin
				? java.util.List.of(
						"How many orders need processing?",
						"Which products are low on stock?",
						"Summarize sales for the last 30 days."
				)
				: java.util.List.of(
						"What is the status of my latest order?",
						"Show available products under 2000000.",
						"How do I complete checkout?"
				);
		return ResponseEntity.ok(java.util.Map.of("language", "EN", "prompts", prompts));
	}

	/**
	 * Sends at most one terminal SSE event, then completes the emitter cleanly.
	 * Never uses {@code completeWithError} after writing events — that aborts chunked
	 * transfer encoding and surfaces as {@code ERR_INCOMPLETE_CHUNKED_ENCODING}.
	 */
	private static void sendTerminal(
			SseEmitter emitter,
			AtomicBoolean terminalSent,
			String streamId,
			String eventName,
			String data
	) {
		if (!terminalSent.compareAndSet(false, true)) {
			log.debug("chat_sse streamId={} stage=duplicate_terminal ignored={}", streamId, eventName);
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(eventName).data(data));
			emitter.complete();
		}
		catch (IOException | IllegalStateException ex) {
			log.info(
					"chat_sse streamId={} stage=terminal_write_failed event={} type={}",
					streamId,
					eventName,
					ex.getClass().getSimpleName()
			);
			try {
				emitter.complete();
			}
			catch (Exception ignored) {
				// response already closed
			}
		}
	}

	private static void safeComplete(SseEmitter emitter, AtomicBoolean terminalSent) {
		if (!terminalSent.compareAndSet(false, true)) {
			return;
		}
		try {
			emitter.complete();
		}
		catch (Exception ignored) {
			// already closed
		}
	}

	private static String safeErrorMessage(ApiException ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return "Assistant stream failed";
		}
		return message.length() > 240 ? message.substring(0, 240) : message;
	}

	private static final class ClientGoneException extends RuntimeException {
		private ClientGoneException(IOException cause) {
			super(cause);
		}
	}
}
