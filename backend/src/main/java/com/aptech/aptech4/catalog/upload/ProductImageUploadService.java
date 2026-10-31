package com.aptech.aptech4.catalog.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aptech.aptech4.catalog.api.ProductImageUploadResponse;
import com.aptech.aptech4.common.api.ApiException;

@Service
public class ProductImageUploadService {

	private final ProductImageProperties properties;

	public ProductImageUploadService(ProductImageProperties properties) {
		this.properties = properties;
	}

	public ProductImageUploadResponse upload(MultipartFile file) {
		if (!properties.isEnabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", "Product image upload is disabled");
		}
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Missing or empty file");
		}
		long declaredSize = file.getSize();
		if (declaredSize >= properties.getMaxSizeBytes()) {
			throw oversized();
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unable to read uploaded file");
		}
		if (bytes.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Missing or empty file");
		}
		if (bytes.length >= properties.getMaxSizeBytes()) {
			throw oversized();
		}

		DetectedImageFormat format = detectFormat(bytes);
		Set<String> allowed = properties.allowedContentTypes();
		if (!allowed.contains(format.contentType())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unsupported image format");
		}

		ImageMetrics metrics = decodeMetrics(bytes, format);
		if (metrics.width() > properties.getMaxWidth() || metrics.height() > properties.getMaxHeight()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Image dimensions exceed configured limits");
		}
		long pixels = (long) metrics.width() * (long) metrics.height();
		if (pixels > properties.getMaxPixels()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Image pixel count exceeds configured limit");
		}

		byte[] toStore = bytes;
		DetectedImageFormat storeFormat = format;
		if (properties.isNormalizeEnabled()) {
			toStore = normalizeToJpeg(bytes);
			storeFormat = DetectedImageFormat.JPEG;
			if (toStore.length >= properties.getMaxSizeBytes()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Normalized image exceeds size limit");
			}
		}

		String filename = UUID.randomUUID() + "." + storeFormat.extension();
		Path uploadRoot = properties.uploadDirectory();
		Path target = uploadRoot.resolve(filename).normalize();
		if (!target.startsWith(uploadRoot)) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "Invalid storage path");
		}

		Path temp = null;
		try {
			temp = Files.createTempFile(uploadRoot, ".upload-", ".tmp");
			Files.write(temp, toStore);
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			temp = null;
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "Failed to store uploaded image");
		}
		finally {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				}
				catch (IOException ignored) {
					// best-effort cleanup
				}
			}
		}

		String publicUrl = properties.getPublicPath() + "/" + filename;
		return new ProductImageUploadResponse(publicUrl, storeFormat.contentType(), toStore.length);
	}

	private ApiException oversized() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"Bad Request",
				"Image must be smaller than " + properties.getMaxSizeBytes() + " bytes"
		);
	}

	static DetectedImageFormat detectFormat(byte[] bytes) {
		if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
			return DetectedImageFormat.JPEG;
		}
		if (bytes.length >= 8
				&& (bytes[0] & 0xFF) == 0x89
				&& bytes[1] == 0x50
				&& bytes[2] == 0x4E
				&& bytes[3] == 0x47
				&& bytes[4] == 0x0D
				&& bytes[5] == 0x0A
				&& bytes[6] == 0x1A
				&& bytes[7] == 0x0A) {
			return DetectedImageFormat.PNG;
		}
		if (bytes.length >= 12
				&& bytes[0] == 'R'
				&& bytes[1] == 'I'
				&& bytes[2] == 'F'
				&& bytes[3] == 'F'
				&& bytes[8] == 'W'
				&& bytes[9] == 'E'
				&& bytes[10] == 'B'
				&& bytes[11] == 'P') {
			return DetectedImageFormat.WEBP;
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unsupported or unrecognized image format");
	}

	private ImageMetrics decodeMetrics(byte[] bytes, DetectedImageFormat format) {
		try (InputStream in = new ByteArrayInputStream(bytes);
				ImageInputStream iis = ImageIO.createImageInputStream(in)) {
			if (iis == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, true);
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width < 1 || height < 1) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
				}
				// Force a decode pass to catch truncated payloads
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
				}
				String formatName = reader.getFormatName() == null ? "" : reader.getFormatName().toLowerCase(Locale.ROOT);
				if (format == DetectedImageFormat.JPEG && !(formatName.contains("jpeg") || formatName.contains("jpg"))) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Image content does not match JPEG");
				}
				if (format == DetectedImageFormat.PNG && !formatName.contains("png")) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Image content does not match PNG");
				}
				if (format == DetectedImageFormat.WEBP && !formatName.contains("webp")) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Image content does not match WebP");
				}
				return new ImageMetrics(width, height);
			}
			finally {
				reader.dispose();
			}
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
		}
	}

	private byte[] normalizeToJpeg(byte[] bytes) {
		try (InputStream in = new ByteArrayInputStream(bytes)) {
			BufferedImage image = ImageIO.read(in);
			if (image == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid or corrupted image");
			}
			// JPEG has no alpha — flatten onto white so ARGB/WebP transparency does not fail ImageIO.write.
			BufferedImage rgb = image;
			if (image.getColorModel().hasAlpha() || image.getType() == BufferedImage.TYPE_INT_ARGB) {
				rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
				java.awt.Graphics2D g = rgb.createGraphics();
				g.setColor(java.awt.Color.WHITE);
				g.fillRect(0, 0, image.getWidth(), image.getHeight());
				g.drawImage(image, 0, 0, null);
				g.dispose();
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			if (!ImageIO.write(rgb, "jpg", out)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unable to normalize image");
			}
			return out.toByteArray();
		}
		catch (ApiException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Unable to normalize image");
		}
	}

	private record ImageMetrics(int width, int height) {
	}
}
