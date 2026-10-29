package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ChatToolEnvelopeTest {

	@Test
	void successEmptyVsNonEmpty() {
		Map<String, Object> empty = ChatToolEnvelope.success("t", Map.of("items", List.of()), 0, 1L);
		assertThat(empty.get("status")).isEqualTo(ChatToolEnvelope.SUCCESS_EMPTY);
		assertThat(empty.get("message").toString()).contains("zero matching");

		Map<String, Object> filled = ChatToolEnvelope.success("t", Map.of("items", List.of(Map.of("a", 1))), 1, 2L);
		assertThat(filled.get("status")).isEqualTo(ChatToolEnvelope.SUCCESS_NONEMPTY);
	}

	@Test
	void failureNeverLooksLikeEmptyList() {
		Map<String, Object> failure = ChatToolEnvelope.failure(
				"t",
				ChatToolEnvelope.ERROR_AUTHORIZATION,
				"denied",
				3L
		);
		assertThat(failure.get("status")).isEqualTo(ChatToolEnvelope.FAILURE);
		assertThat(failure.get("result")).isNull();
		assertThat(failure.get("resultSize")).isNull();
		assertThat(failure.get("errorCategory")).isEqualTo(ChatToolEnvelope.ERROR_AUTHORIZATION);
	}
}
