package com.aptech.aptech4.common.locale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure Accept-Language resolver for {@link AppLocale}.
 *
 * <p><strong>Policy (deterministic):</strong>
 * <ul>
 *   <li>Missing/blank header → {@link AppLocale#DEFAULT} ({@code vi}).</li>
 *   <li>Tags are matched case-insensitively; regional tags use the primary subtag
 *       ({@code en-US}/{@code en-GB} → {@code en}, {@code vi-VN} → {@code vi}).</li>
 *   <li>Entries are ordered by quality {@code q} descending; equal {@code q} keeps header order.</li>
 *   <li>Missing {@code q} → {@code 1.0}. {@code q=0} means not acceptable (skipped).</li>
 *   <li>Non-numeric, empty, or out-of-range {@code q} (not in {@code [0,1]}) → entry ignored.</li>
 *   <li>Malformed tags / empty segments → skipped.</li>
 *   <li>Wildcard {@code *} never selects a concrete locale; if it ranks first it is skipped so a
 *       lower-{@code q} supported language can win. If only wildcards/unsupported remain → {@code vi}.</li>
 *   <li>No supported language after filtering → {@code vi}.</li>
 * </ul>
 */
public final class AcceptLanguageParser {

	private AcceptLanguageParser() {
	}

	public static AppLocale resolve(String acceptLanguageHeader) {
		if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
			return AppLocale.DEFAULT;
		}

		List<WeightedLanguage> candidates = new ArrayList<>();
		String[] parts = acceptLanguageHeader.split(",");
		int order = 0;
		for (String rawPart : parts) {
			WeightedLanguage parsed = parseEntry(rawPart, order);
			if (parsed != null) {
				candidates.add(parsed);
				order++;
			}
		}

		if (candidates.isEmpty()) {
			return AppLocale.DEFAULT;
		}

		candidates.sort(Comparator
				.comparingDouble(WeightedLanguage::quality).reversed()
				.thenComparingInt(WeightedLanguage::order));

		for (WeightedLanguage candidate : candidates) {
			if (candidate.wildcard()) {
				continue;
			}
			AppLocale locale = AppLocale.fromLanguageCode(candidate.primarySubtag());
			if (locale != null) {
				return locale;
			}
		}

		return AppLocale.DEFAULT;
	}

	private static WeightedLanguage parseEntry(String rawPart, int order) {
		if (rawPart == null) {
			return null;
		}
		String part = rawPart.trim();
		if (part.isEmpty()) {
			return null;
		}

		String[] tokens = part.split(";");
		String tagToken = tokens[0].trim();
		if (tagToken.isEmpty()) {
			return null;
		}

		Double quality = 1.0d;
		for (int i = 1; i < tokens.length; i++) {
			String param = tokens[i].trim();
			if (param.isEmpty()) {
				continue;
			}
			int eq = param.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String name = param.substring(0, eq).trim();
			if (!"q".equalsIgnoreCase(name)) {
				continue;
			}
			String value = param.substring(eq + 1).trim();
			quality = parseQuality(value);
			if (quality == null) {
				return null;
			}
		}

		if (quality <= 0.0d) {
			return null;
		}

		if ("*".equals(tagToken)) {
			return new WeightedLanguage(true, "", quality, order);
		}

		String primary = primarySubtag(tagToken);
		if (primary == null) {
			return null;
		}
		return new WeightedLanguage(false, primary, quality, order);
	}

	/**
	 * @return quality in {@code [0,1]}, or {@code null} if invalid
	 */
	private static Double parseQuality(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			double value = Double.parseDouble(raw);
			if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
				return null;
			}
			return value;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Extracts the primary language subtag; returns {@code null} when the tag is unusable.
	 */
	private static String primarySubtag(String tagToken) {
		String normalized = tagToken.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || "*".equals(normalized)) {
			return null;
		}
		// Reject obvious garbage (spaces, leading punctuation).
		if (!Character.isLetter(normalized.charAt(0))) {
			return null;
		}
		int slash = normalized.indexOf('/');
		if (slash >= 0) {
			normalized = normalized.substring(0, slash);
		}
		int sep = normalized.indexOf('-');
		if (sep < 0) {
			sep = normalized.indexOf('_');
		}
		String primary = sep < 0 ? normalized : normalized.substring(0, sep);
		if (primary.isEmpty() || !primary.chars().allMatch(Character::isLetter)) {
			return null;
		}
		return primary;
	}

	private record WeightedLanguage(boolean wildcard, String primarySubtag, double quality, int order) {
	}
}
