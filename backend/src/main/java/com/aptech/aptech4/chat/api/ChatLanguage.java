package com.aptech.aptech4.chat.api;

/**
 * UI / reply language. API and tool identifiers stay English.
 */
public enum ChatLanguage {
	EN,
	VI;

	public static ChatLanguage fromNullable(String raw) {
		if (raw == null || raw.isBlank()) {
			return EN;
		}
		return switch (raw.trim().toUpperCase()) {
			case "VI", "VN", "VIETNAMESE" -> VI;
			default -> EN;
		};
	}
}
