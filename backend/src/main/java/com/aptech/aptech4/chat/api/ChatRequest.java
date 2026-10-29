package com.aptech.aptech4.chat.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
		@NotBlank @Size(max = 2000) String message,
		UUID conversationId,
		/**
		 * Optional explicit language. When null, server detects from the message
		 * (or keeps the conversation preference).
		 */
		String language
) {
}
