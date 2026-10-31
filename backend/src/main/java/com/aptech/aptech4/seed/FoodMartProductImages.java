package com.aptech.aptech4.seed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.aptech.aptech4.catalog.upload.ProductImagePathRules;
import com.aptech.aptech4.catalog.upload.ProductImageProperties;
import com.aptech.aptech4.catalog.upload.UploadPurpose;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Copies FoodMart product artwork from classpath into the product uploads directory and returns
 * public {@code /uploads/products/{uuid}.ext} paths for seed persistence.
 */
@Component
public class FoodMartProductImages {

	private static final Logger log = LoggerFactory.getLogger(FoodMartProductImages.class);
	private static final String MAP_RESOURCE = "db/demo/foodmart-product-images.json";
	private static final String ASSET_PREFIX = "db/demo/product-images/";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ObjectProvider<ProductImageProperties> productImageProperties;

	private volatile Map<String, String> skuToAsset;

	public FoodMartProductImages(ObjectProvider<ProductImageProperties> productImageProperties) {
		this.productImageProperties = productImageProperties;
	}

	/**
	 * Ensures the on-disk seed asset exists and returns the normalized public path for {@code sku}.
	 */
	public String ensurePersistedImageUrl(String sku) {
		String assetFile = assetFileName(sku);
		if (assetFile == null) {
			throw new IllegalStateException("No FoodMart seed image mapping for SKU " + sku);
		}
		String ext = extensionOf(assetFile);
		UUID id = deterministicId(sku);
		String publicPath = ProductImagePathRules.normalizeOrNull(
				"/uploads/products/" + id + "." + ext
		);
		if (publicPath == null) {
			throw new IllegalStateException("Invalid seeded product image path for SKU " + sku);
		}

		ProductImageProperties props = productImageProperties.getIfAvailable();
		if (props == null) {
			log.warn("ProductImageProperties unavailable — skipping file write for {}", sku);
			return publicPath;
		}

		Path dir = props.uploadDirectory(UploadPurpose.PRODUCT);
		String filename = publicPath.substring(publicPath.lastIndexOf('/') + 1);
		Path target = dir.resolve(filename);
		ClassPathResource resource = new ClassPathResource(ASSET_PREFIX + assetFile);
		if (!resource.exists()) {
			throw new IllegalStateException("Missing classpath product image " + ASSET_PREFIX + assetFile);
		}
		try {
			Files.createDirectories(dir);
			try (InputStream in = resource.getInputStream()) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot write seed product image to " + target, ex);
		}
		return publicPath;
	}

	public Map<String, String> skuAssetMap() {
		return Collections.unmodifiableMap(loadMap());
	}

	/** Deterministic UUID used for the public filename (stable across seed reruns). */
	public static UUID deterministicId(String sku) {
		return UUID.nameUUIDFromBytes(("aptech4-foodmart-product:" + sku).getBytes(StandardCharsets.UTF_8));
	}

	private String assetFileName(String sku) {
		return loadMap().get(sku);
	}

	private Map<String, String> loadMap() {
		Map<String, String> local = skuToAsset;
		if (local != null) {
			return local;
		}
		synchronized (this) {
			if (skuToAsset == null) {
				skuToAsset = readMap();
			}
			return skuToAsset;
		}
	}

	private Map<String, String> readMap() {
		try (InputStream in = new ClassPathResource(MAP_RESOURCE).getInputStream()) {
			Document doc = MAPPER.readValue(in, Document.class);
			if (doc.assets() == null || doc.assets().isEmpty()) {
				throw new IllegalStateException(MAP_RESOURCE + " has no assets");
			}
			Map<String, String> cleaned = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : doc.assets().entrySet()) {
				String sku = entry.getKey();
				String asset = entry.getValue();
				if (sku == null || sku.startsWith("_") || asset == null || asset.isBlank()) {
					continue;
				}
				if (asset.contains("..") || asset.contains("/") || asset.contains("\\")) {
					throw new IllegalStateException("Illegal asset path for " + sku + ": " + asset);
				}
				cleaned.put(sku, asset.trim());
			}
			if (cleaned.size() != FoodMartCatalog.products().size()) {
				throw new IllegalStateException(
						"FoodMart product image map size " + cleaned.size()
								+ " does not match catalog product count "
								+ FoodMartCatalog.products().size()
				);
			}
			for (var product : FoodMartCatalog.products()) {
				if (!cleaned.containsKey(product.sku())) {
					throw new IllegalStateException("Missing image mapping for " + product.sku());
				}
			}
			return Map.copyOf(cleaned);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load " + MAP_RESOURCE, ex);
		}
	}

	private static String extensionOf(String assetFile) {
		int dot = assetFile.lastIndexOf('.');
		if (dot < 0) {
			throw new IllegalStateException("Asset has no extension: " + assetFile);
		}
		String ext = assetFile.substring(dot + 1).toLowerCase(Locale.ROOT);
		if ("jpeg".equals(ext)) {
			return "jpg";
		}
		if (!ext.equals("jpg") && !ext.equals("png") && !ext.equals("webp")) {
			throw new IllegalStateException("Unsupported seed image extension: " + assetFile);
		}
		return ext;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Document(Map<String, String> assets) {
	}
}
