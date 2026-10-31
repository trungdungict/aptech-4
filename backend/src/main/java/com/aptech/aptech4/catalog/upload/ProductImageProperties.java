package com.aptech.aptech4.catalog.upload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * Product image upload settings (env-backed). Backend remains authoritative over frontend VITE_* mirrors.
 */
@ConfigurationProperties(prefix = "aptech.product-image")
public class ProductImageProperties {

	/** Strict upper bound: files with {@code size >= maxSizeBytes} are rejected (exact 2 MiB rejected by default). */
	public static final long DEFAULT_MAX_SIZE_BYTES = 2L * 1024 * 1024;

	private boolean enabled = true;
	private String uploadDir = "./.data/uploads/products";
	private String publicPath = "/uploads/products";
	private long maxSizeBytes = DEFAULT_MAX_SIZE_BYTES;
	private String allowedTypes = "image/jpeg,image/png,image/webp";
	private int maxWidth = 6000;
	private int maxHeight = 6000;
	private long maxPixels = 25_000_000L;
	private boolean normalizeEnabled = false;

	@PostConstruct
	public void validate() {
		if (!StringUtils.hasText(uploadDir)) {
			throw new IllegalStateException("PRODUCT_IMAGE_UPLOAD_DIR / aptech.product-image.upload-dir must be set");
		}
		if (!StringUtils.hasText(publicPath) || !publicPath.startsWith("/") || publicPath.contains("..")) {
			throw new IllegalStateException(
					"PRODUCT_IMAGE_PUBLIC_PATH must be a path starting with / and must not contain '..'"
			);
		}
		if (maxSizeBytes < 1) {
			throw new IllegalStateException("PRODUCT_IMAGE_MAX_SIZE_BYTES must be >= 1");
		}
		if (maxWidth < 1 || maxHeight < 1) {
			throw new IllegalStateException("PRODUCT_IMAGE_MAX_WIDTH/HEIGHT must be >= 1");
		}
		if (maxPixels < 1) {
			throw new IllegalStateException("PRODUCT_IMAGE_MAX_PIXELS must be >= 1");
		}
		if (allowedContentTypes().isEmpty()) {
			throw new IllegalStateException("PRODUCT_IMAGE_ALLOWED_TYPES must list at least one MIME type");
		}
		Path dir = Path.of(uploadDir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(dir);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Cannot create product image upload directory: " + dir, ex);
		}
		if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
			throw new IllegalStateException("Product image upload directory is not a writable directory: " + dir);
		}
		this.uploadDir = dir.toString();
		this.publicPath = publicPath.endsWith("/") ? publicPath.substring(0, publicPath.length() - 1) : publicPath;
	}

	public Set<String> allowedContentTypes() {
		return Arrays.stream(allowedTypes.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.map(s -> s.toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public Path uploadDirectory() {
		return Path.of(uploadDir).toAbsolutePath().normalize();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUploadDir() {
		return uploadDir;
	}

	public void setUploadDir(String uploadDir) {
		this.uploadDir = uploadDir;
	}

	public String getPublicPath() {
		return publicPath;
	}

	public void setPublicPath(String publicPath) {
		this.publicPath = publicPath;
	}

	public long getMaxSizeBytes() {
		return maxSizeBytes;
	}

	public void setMaxSizeBytes(long maxSizeBytes) {
		this.maxSizeBytes = maxSizeBytes;
	}

	public String getAllowedTypes() {
		return allowedTypes;
	}

	public void setAllowedTypes(String allowedTypes) {
		this.allowedTypes = allowedTypes;
	}

	public int getMaxWidth() {
		return maxWidth;
	}

	public void setMaxWidth(int maxWidth) {
		this.maxWidth = maxWidth;
	}

	public int getMaxHeight() {
		return maxHeight;
	}

	public void setMaxHeight(int maxHeight) {
		this.maxHeight = maxHeight;
	}

	public long getMaxPixels() {
		return maxPixels;
	}

	public void setMaxPixels(long maxPixels) {
		this.maxPixels = maxPixels;
	}

	public boolean isNormalizeEnabled() {
		return normalizeEnabled;
	}

	public void setNormalizeEnabled(boolean normalizeEnabled) {
		this.normalizeEnabled = normalizeEnabled;
	}
}
