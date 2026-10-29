package com.aptech.aptech4.chat.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.aptech.aptech4.chat.api.ChatLanguage;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.common.api.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Bounded in-memory conversation store scoped by authenticated user.
 * Cleared on process restart; not shared across users/roles.
 */
@Service
public class ChatSessionService {

	private final AiProperties aiProperties;
	private final Map<UUID, Conversation> conversations = new ConcurrentHashMap<>();

	public ChatSessionService(AiProperties aiProperties) {
		this.aiProperties = aiProperties;
	}

	public Conversation getOrCreate(UUID userId, UUID conversationId, boolean admin) {
		if (conversationId == null) {
			Conversation created = Conversation.newConversation(userId, admin);
			conversations.put(created.id(), created);
			return created;
		}
		Conversation existing = conversations.get(conversationId);
		if (existing == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Conversation not found");
		}
		if (!existing.userId().equals(userId) || existing.admin() != admin) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Conversation does not belong to this user");
		}
		return existing;
	}

	public void clearForUser(UUID userId) {
		conversations.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
	}

	public record Conversation(
			UUID id,
			UUID userId,
			boolean admin,
			List<Message> messages,
			ChatLanguage languagePreference,
			Instant updatedAt
	) {
		static Conversation newConversation(UUID userId, boolean admin) {
			// null preference → ChatLanguageResolver can detect VI diacritics on the first turn
			// (defaulting to EN here permanently blocked detection).
			return new Conversation(UUID.randomUUID(), userId, admin, new ArrayList<>(), null, Instant.now());
		}

		Conversation withLanguage(ChatLanguage language) {
			return new Conversation(id, userId, admin, messages, language, Instant.now());
		}

		void appendTurn(String userText, String assistantText, int maxHistory) {
			messages.add(new UserMessage(userText));
			messages.add(new AssistantMessage(assistantText));
			while (messages.size() > maxHistory) {
				messages.removeFirst();
			}
		}

		List<Message> historySnapshot() {
			return List.copyOf(messages);
		}
	}

	public void save(Conversation conversation) {
		conversations.put(conversation.id(), conversation);
	}

	public int maxHistory() {
		return Math.max(2, aiProperties.getMaxHistoryMessages());
	}
}
