package com.aptech.aptech4.catalog.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.aptech.aptech4.catalog.api.ProductImageUploadResponse;
import com.aptech.aptech4.common.api.ApiException;

class ProductImageUploadServiceTest {

	private static final long MAX_BYTES = 2L * 1024 * 1024;

	/**
	 * Minimal 1×1 lossy WebP (RIFF/WEBP/VP8). Decoded via TwelveMonkeys when on the classpath.
	 */
	private static final byte[] TINY_WEBP = Base64.getDecoder().decode(
			"UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA="
	);

	@TempDir
	Path tempDir;

	ProductImageProperties properties;
	ProductImageUploadService service;

	@BeforeEach
	void setUp() {
		properties = new ProductImageProperties();
		properties.setUploadDir(tempDir.resolve("products").toString());
		properties.setPublicPath("/uploads/products");
		properties.setMaxSizeBytes(MAX_BYTES);
		properties.setMaxWidth(6000);
		properties.setMaxHeight(6000);
		properties.setMaxPixels(25_000_000L);
		properties.setAllowedTypes("image/jpeg,image/png,image/webp");
		properties.setNormalizeEnabled(false);
		properties.setEnabled(true);
		properties.validate();
		service = new ProductImageUploadService(properties);
	}

	@Test
	void acceptsValidJpegPngAndWebp() throws Exception {
		ProductImageUploadResponse jpeg = service.upload(multipart("a.jpg", "image/jpeg", tinyJpeg(8, 8)));
		assertThat(jpeg.imageUrl()).endsWith(".jpg");
		assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
		assertStoredUnderUploadDir(jpeg.imageUrl());

		ProductImageUploadResponse png = service.upload(multipart("a.png", "image/png", tinyPng(8, 8)));
		assertThat(png.imageUrl()).endsWith(".png");
		assertThat(png.contentType()).isEqualTo("image/png");

		ProductImageUploadResponse webp = service.upload(multipart("a.webp", "image/webp", TINY_WEBP));
		assertThat(webp.imageUrl()).endsWith(".webp");
		assertThat(webp.contentType()).isEqualTo("image/webp");
		assertThat(webp.sizeBytes()).isEqualTo(TINY_WEBP.length);
	}

	@Test
	void acceptsFileBelowConfiguredLimit() throws Exception {
		byte[] png = tinyPng(16, 16);
		assertThat(png.length).isLessThan((int) MAX_BYTES);
		ProductImageUploadResponse response = service.upload(multipart("ok.png", "image/png", png));
		assertThat(response.sizeBytes()).isEqualTo(png.length);
	}

	@Test
	void rejectsExactMaxSizeBytes() throws Exception {
		byte[] exact = padToSize(tinyPng(4, 4), (int) MAX_BYTES);
		assertThatThrownBy(() -> service.upload(multipart("exact.png", "image/png", exact)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("smaller than");
		assertNoOrphanTempsOrUnexpectedFiles(0);
	}

	@Test
	void rejectsAboveMaxSizeBytes() throws Exception {
		byte[] over = padToSize(tinyPng(4, 4), (int) MAX_BYTES + 1);
		assertThatThrownBy(() -> service.upload(multipart("over.png", "image/png", over)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("smaller than");
	}

	@Test
	void rejectsEmptyFile() {
		assertThatThrownBy(() -> service.upload(multipart("empty.png", "image/png", new byte[0])))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("empty");
	}

	@Test
	void rejectsSvgGifAndPdf() throws Exception {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> service.upload(multipart("x.svg", "image/svg+xml", svg)))
				.isInstanceOf(ApiException.class);

		byte[] gif = tinyGif();
		assertThatThrownBy(() -> service.upload(multipart("x.gif", "image/gif", gif)))
				.isInstanceOf(ApiException.class);

		byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> service.upload(multipart("x.pdf", "application/pdf", pdf)))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsMimeAndExtensionSpoofing() {
		byte[] notImage = "MZ-executable-not-an-image".getBytes(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> service.upload(multipart("evil.png", "image/png", notImage)))
				.isInstanceOf(ApiException.class);

		assertThatThrownBy(() -> service.upload(multipart("evil.jpg", "image/jpeg", notImage)))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsCorruptedImageBytes() throws Exception {
		byte[] truncatedPng = new byte[] {
				(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01
		};
		assertThatThrownBy(() -> service.upload(multipart("bad.png", "image/png", truncatedPng)))
				.isInstanceOf(ApiException.class);
		assertNoOrphanTempsOrUnexpectedFiles(0);
	}

	@Test
	void enforcesMaxWidthHeightAndPixels() throws Exception {
		properties.setMaxWidth(10);
		properties.setMaxHeight(10);
		properties.setMaxPixels(10_000L);
		properties.validate();
		service = new ProductImageUploadService(properties);

		byte[] wide = tinyPng(40, 8);
		assertThatThrownBy(() -> service.upload(multipart("wide.png", "image/png", wide)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("dimensions");

		properties.setMaxWidth(6000);
		properties.setMaxHeight(6000);
		properties.setMaxPixels(50L);
		properties.validate();
		service = new ProductImageUploadService(properties);

		byte[] dense = tinyPng(16, 16); // 256 pixels
		assertThatThrownBy(() -> service.upload(multipart("dense.png", "image/png", dense)))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("pixel");
	}

	@Test
	void generatesUuidFilenameAndIgnoresOriginalNamePathTraversal() throws Exception {
		byte[] png = tinyPng(8, 8);
		ProductImageUploadResponse response = service.upload(
				multipart("../../etc/passwd.png", "image/png", png)
		);
		String name = Path.of(response.imageUrl()).getFileName().toString();
		assertThat(name).doesNotContain("passwd").doesNotContain("..");
		assertThat(name).matches("[0-9a-fA-F-]{36}\\.png");
		assertStoredUnderUploadDir(response.imageUrl());
		try (Stream<Path> stream = Files.list(tempDir)) {
			assertThat(stream.count()).isEqualTo(1); // only products/
		}
	}

	@Test
	void uploadDisabledRejects() throws Exception {
		properties.setEnabled(false);
		assertThatThrownBy(() -> service.upload(multipart("a.png", "image/png", tinyPng(4, 4))))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("disabled");
	}

	@Test
	void normalizationDisabledPreservesFormat() throws Exception {
		properties.setNormalizeEnabled(false);
		ProductImageUploadResponse png = service.upload(multipart("a.png", "image/png", tinyPng(8, 8)));
		assertThat(png.contentType()).isEqualTo("image/png");
		assertThat(png.imageUrl()).endsWith(".png");
	}

	@Test
	void normalizationEnabledStoresJpegEvenForPngInput() throws Exception {
		properties.setNormalizeEnabled(true);
		byte[] png = tinyPng(12, 12);
		ProductImageUploadResponse response = service.upload(multipart("a.png", "image/png", png));
		assertThat(response.contentType()).isEqualTo("image/jpeg");
		assertThat(response.imageUrl()).endsWith(".jpg");
		Path stored = storedPath(response.imageUrl());
		assertThat(Files.exists(stored)).isTrue();
		BufferedImage decoded = ImageIO.read(stored.toFile());
		assertThat(decoded).isNotNull();
		assertThat(decoded.getWidth()).isEqualTo(12);
	}

	@Test
	void normalizationHandlesTransparentPngAsJpeg() throws Exception {
		properties.setNormalizeEnabled(true);
		byte[] transparent = tinyTransparentPng(10, 10);
		ProductImageUploadResponse response = service.upload(multipart("t.png", "image/png", transparent));
		assertThat(response.imageUrl()).endsWith(".jpg");
		assertThat(response.contentType()).isEqualTo("image/jpeg");
		BufferedImage decoded = ImageIO.read(storedPath(response.imageUrl()).toFile());
		assertThat(decoded).isNotNull();
		assertThat(decoded.getColorModel().hasAlpha()).isFalse();
	}

	@Test
	void storageFailureCleansTempAndLeavesNoPartialPublicFile() throws Exception {
		Path uploadDir = properties.uploadDirectory();
		assumeTrue(uploadDir.toFile().setWritable(false), "filesystem cannot make upload dir read-only");
		try {
			assertThatThrownBy(() -> service.upload(multipart("a.png", "image/png", tinyPng(8, 8))))
					.isInstanceOf(ApiException.class)
					.hasMessageContaining("Failed to store");
		}
		finally {
			assumeTrue(uploadDir.toFile().setWritable(true));
		}
		assertNoOrphanTempsOrUnexpectedFiles(0);
	}

	@Test
	void invalidMaxSizeFailsConfigurationValidation() {
		ProductImageProperties bad = new ProductImageProperties();
		bad.setUploadDir(tempDir.resolve("bad").toString());
		bad.setMaxSizeBytes(0);
		assertThatThrownBy(bad::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PRODUCT_IMAGE_MAX_SIZE_BYTES");
	}

	private void assertStoredUnderUploadDir(String imageUrl) throws Exception {
		Path stored = storedPath(imageUrl);
		assertThat(stored.startsWith(properties.uploadDirectory())).isTrue();
		assertThat(Files.isRegularFile(stored)).isTrue();
	}

	private Path storedPath(String imageUrl) {
		return properties.uploadDirectory().resolve(Path.of(imageUrl).getFileName().toString());
	}

	private void assertNoOrphanTempsOrUnexpectedFiles(long expectedRegularFiles) throws Exception {
		Path dir = properties.uploadDirectory();
		try (Stream<Path> stream = Files.list(dir)) {
			long temps = stream.filter(p -> {
				String name = p.getFileName().toString();
				return name.startsWith(".upload-") && name.endsWith(".tmp");
			}).count();
			assertThat(temps).isZero();
		}
		try (Stream<Path> stream = Files.list(dir)) {
			long files = stream.filter(Files::isRegularFile).count();
			assertThat(files).isEqualTo(expectedRegularFiles);
		}
	}

	private static MockMultipartFile multipart(String filename, String contentType, byte[] bytes) {
		return new MockMultipartFile("file", filename, contentType, bytes);
	}

	private static byte[] padToSize(byte[] prefix, int size) {
		byte[] out = new byte[size];
		System.arraycopy(prefix, 0, out, 0, Math.min(prefix.length, size));
		return out;
	}

	private static byte[] tinyPng(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.GREEN);
		g.fillRect(0, 0, width, height);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] tinyTransparentPng(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, (x + y) % 2 == 0 ? 0x00FFFFFF : 0xFF336699);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static byte[] tinyJpeg(int width, int height) throws Exception {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.BLUE.getRGB());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	private static byte[] tinyGif() throws Exception {
		BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.ORANGE.getRGB());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assumeTrue(ImageIO.write(image, "gif", out), "GIF writer unavailable");
		return out.toByteArray();
	}
}
