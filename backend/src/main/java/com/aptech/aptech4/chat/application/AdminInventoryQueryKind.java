package com.aptech.aptech4.chat.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detects admin inventory intents that must be answered from structured tool data
 * (never free-form LLM invention of SKUs / quantities).
 */
public enum AdminInventoryQueryKind {
	LOW_STOCK,
	OUT_OF_STOCK,
	RISK_SUMMARY,
	NONE;

	private static final Pattern OUT = Pattern.compile(
			"\\b(out[- ]?of[- ]?stock|sold[- ]?out|h[eế]t\\s*h[aà]ng|không\\s*còn\\s*hàng)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern LOW = Pattern.compile(
			"\\b(low[- ]?stock|low\\s+on\\s+stock|running\\s+low|s[aắ]p\\s*h[eế]t|t[oồ]n\\s*kho\\s*th[aấ]p)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern RISK = Pattern.compile(
			"\\b(inventory\\s+risk|stock\\s+risk|inventory\\s+summary|t[oồ]n\\s*kho|inventory)\\b",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	public static AdminInventoryQueryKind detect(String message) {
		if (message == null || message.isBlank()) {
			return NONE;
		}
		String text = message.toLowerCase(Locale.ROOT);
		boolean out = OUT.matcher(text).find();
		boolean low = LOW.matcher(text).find();
		if (out && low) {
			return RISK_SUMMARY;
		}
		if (out) {
			return OUT_OF_STOCK;
		}
		if (low) {
			return LOW_STOCK;
		}
		if (RISK.matcher(text).find() && (text.contains("stock") || text.contains("inventory")
				|| text.contains("tồn") || text.contains("ton kho"))) {
			return RISK_SUMMARY;
		}
		return NONE;
	}
}
