package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ChatMetricsTest {

	@Test
	void recordsBoundedLabelsOnly() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ChatMetrics metrics = new ChatMetrics(registry);

		assertThat(registry.find("aptech_chat_requests_total")
				.tag("endpoint", "rest")
				.tag("handler", "deterministic")
				.tag("outcome", "success")
				.counter()).isNotNull();
		assertThat(registry.find("aptech_chat_sse_disconnects_total").counter().count()).isEqualTo(0.0);

		metrics.recordRequest("stream", "deterministic", "success", 42);
		metrics.recordSseDisconnect();
		metrics.recordSseError();

		assertThat(registry.find("aptech_chat_requests_total")
				.tag("endpoint", "stream")
				.tag("handler", "deterministic")
				.tag("outcome", "success")
				.counter().count()).isEqualTo(1.0);
		assertThat(registry.find("aptech_chat_sse_disconnects_total").counter().count()).isEqualTo(1.0);
		assertThat(registry.find("aptech_chat_sse_errors_total").counter().count()).isEqualTo(1.0);
		assertThat(ChatMetrics.handlerFromPath("deterministic_orders_by_status")).isEqualTo("deterministic");
		assertThat(ChatMetrics.handlerFromPath("llm")).isEqualTo("llm");
		assertThat(ChatMetrics.handlerFromPath("llm_auth_required")).isEqualTo("llm");
		assertThat(ChatMetrics.handlerFromPath("deterministic_order_count")).isEqualTo("deterministic");
	}
}
