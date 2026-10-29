package com.aptech.aptech4.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serializes tool results as JSON strings for the model (never JPA entities).
 */
public final class ChatToolJson {

	private ChatToolJson() {
	}

	public static String write(ObjectMapper mapper, Object value) {
		try {
			return mapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			return "{\"error\":\"serialization_failed\"}";
		}
	}
}
