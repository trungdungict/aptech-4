package com.aptech.aptech4.chat.api;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
		UUID conversationId,
		String reply,
		String language,
		List<String> suggestedPrompts,
		ChatTiming timing
) {
	public ChatResponse(UUID conversationId, String reply, String language, List<String> suggestedPrompts) {
		this(conversationId, reply, language, suggestedPrompts, null);
	}
}
