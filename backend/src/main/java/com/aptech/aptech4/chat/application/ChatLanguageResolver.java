package com.aptech.aptech4.chat.application;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aptech.aptech4.chat.api.ChatLanguage;

/**
 * Resolves reply language: explicit selection wins; otherwise detect from the user message.
 * Default is English.
 */
@Component
public class ChatLanguageResolver {

	private static final Pattern VIETNAMESE_CHARS = Pattern.compile(
			"[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ"
					+ "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ]"
	);

	public ChatLanguage resolve(String explicitLanguage, String message, ChatLanguage conversationPreference) {
		if (explicitLanguage != null && !explicitLanguage.isBlank()) {
			return ChatLanguage.fromNullable(explicitLanguage);
		}
		if (conversationPreference != null) {
			return conversationPreference;
		}
		return detect(message);
	}

	public ChatLanguage detect(String message) {
		if (message == null || message.isBlank()) {
			return ChatLanguage.EN;
		}
		return VIETNAMESE_CHARS.matcher(message).find() ? ChatLanguage.VI : ChatLanguage.EN;
	}

	public String systemLanguageInstruction(ChatLanguage language) {
		return language == ChatLanguage.VI
				? "Reply in Vietnamese. Keep product names, SKUs and order IDs unchanged."
				: "Reply in English. Keep product names, SKUs and order IDs unchanged.";
	}
}
