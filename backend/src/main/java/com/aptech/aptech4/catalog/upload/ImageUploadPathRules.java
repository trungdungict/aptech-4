package com.aptech.aptech4.catalog.upload;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Purpose-isolated public image path validation (paths only — never bytes in PostgreSQL).
 */
public final class ImageUploadPathRules {

	private static final Pattern PRODUCT_PATH = Pattern.compile(
			"^/uploads/products/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|jpeg|png|webp)$"
	);

	private static final Pattern PROMOTION_PATH = Pattern.compile(
			"^/uploads/promotions/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|jpeg|png|webp)$"
	);

	private ImageUploadPathRules() {
	}

	/**
	 * @return normalized path or {@code null} when blank (clear image)
	 */
	public static String normalizeOrNull(UploadPurpose purpose, String raw) {
		if (purpose == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Upload purpose is required");
		}
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String path = raw.trim();
		if (path.contains("..") || path.contains("\\") || path.contains("://")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid imageUrl");
		}
		Pattern pattern = purpose == UploadPurpose.PROMOTION ? PROMOTION_PATH : PRODUCT_PATH;
		String expectedPrefix = purpose == UploadPurpose.PROMOTION ? "/uploads/promotions/" : "/uploads/products/";
		if (!pattern.matcher(path).matches()) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"imageUrl must be a server upload path under " + expectedPrefix
			);
		}
		String lower = path.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".jpeg")) {
			return path.substring(0, path.length() - 5) + ".jpg";
		}
		return path;
	}
}
