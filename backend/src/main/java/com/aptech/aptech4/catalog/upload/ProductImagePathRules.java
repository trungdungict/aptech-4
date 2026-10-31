package com.aptech.aptech4.catalog.upload;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Validates public product image paths stored in PostgreSQL (relative under /uploads/products only).
 */
public final class ProductImagePathRules {

	private static final Pattern SAFE_PUBLIC_PATH = Pattern.compile(
			"^/uploads/products/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|jpeg|png|webp)$"
	);

	private ProductImagePathRules() {
	}

	/**
	 * @return normalized path or {@code null} when blank (clear image)
	 */
	public static String normalizeOrNull(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String path = raw.trim();
		if (path.contains("..") || path.contains("\\") || path.contains("://")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid imageUrl");
		}
		if (!SAFE_PUBLIC_PATH.matcher(path).matches()) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"imageUrl must be a server upload path under /uploads/products/"
			);
		}
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".jpeg")) {
			return path.substring(0, path.length() - 5) + ".jpg";
		}
		return path;
	}
}
