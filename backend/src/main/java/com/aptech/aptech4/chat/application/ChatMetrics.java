package com.aptech.aptech4.chat.application;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Bounded-cardinality chat metrics for Prometheus (no user/order/prompt labels).
 * Counters are registered at startup so Grafana/PromQL see zeros before first traffic.
 */
@Component
public class ChatMetrics {

	private final MeterRegistry meterRegistry;
	private final Counter sseDisconnects;
	private final Counter sseErrors;

	public ChatMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
		this.sseDisconnects = Counter.builder("aptech_chat_sse_disconnects_total")
				.description("SSE streams closed by client before terminal event")
				.register(meterRegistry);
		this.sseErrors = Counter.builder("aptech_chat_sse_errors_total")
				.description("SSE streams ended with an error terminal event")
				.register(meterRegistry);
		// Pre-register bounded series so PromQL/Grafana see zeros before first traffic.
		for (String endpoint : new String[] { "rest", "stream" }) {
			for (String handler : new String[] { "deterministic", "llm", "unknown" }) {
				Timer.builder("aptech_chat_request_duration_seconds")
						.description("Chat request wall time")
						.tag("endpoint", endpoint)
						.tag("handler", handler)
						.register(meterRegistry);
				for (String outcome : new String[] { "success", "error", "interrupted" }) {
					Counter.builder("aptech_chat_requests_total")
							.description("Customer/Admin chat completions")
							.tag("endpoint", endpoint)
							.tag("handler", handler)
							.tag("outcome", outcome)
							.register(meterRegistry);
				}
			}
		}
	}

	/**
	 * @param endpoint {@code rest} or {@code stream}
	 * @param handler {@code deterministic} or {@code llm}
	 * @param outcome {@code success}, {@code error}, or {@code interrupted}
	 */
	public void recordRequest(String endpoint, String handler, String outcome, long durationMs) {
		Counter.builder("aptech_chat_requests_total")
				.description("Customer/Admin chat completions")
				.tag("endpoint", sanitize(endpoint, "rest", "stream"))
				.tag("handler", sanitize(handler, "deterministic", "llm", "unknown"))
				.tag("outcome", sanitize(outcome, "success", "error", "interrupted"))
				.register(meterRegistry)
				.increment();
		Timer.builder("aptech_chat_request_duration_seconds")
				.description("Chat request wall time")
				.tag("endpoint", sanitize(endpoint, "rest", "stream"))
				.tag("handler", sanitize(handler, "deterministic", "llm", "unknown"))
				.register(meterRegistry)
				.record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
	}

	public void recordSseDisconnect() {
		sseDisconnects.increment();
	}

	public void recordSseError() {
		sseErrors.increment();
	}

	public static String handlerFromPath(String timingPath) {
		if (timingPath == null || timingPath.isBlank()) {
			return "unknown";
		}
		if (timingPath.startsWith("deterministic")) {
			return "deterministic";
		}
		// llm + llm_auth_required stay under the bounded "llm" handler label.
		if (timingPath.equals("llm") || timingPath.startsWith("llm_")) {
			return "llm";
		}
		return "unknown";
	}

	private static String sanitize(String value, String... allowed) {
		if (value == null) {
			return allowed.length > 0 ? allowed[allowed.length - 1] : "unknown";
		}
		for (String candidate : allowed) {
			if (candidate.equals(value)) {
				return candidate;
			}
		}
		return allowed.length > 0 ? allowed[allowed.length - 1] : "unknown";
	}
}
