package com.aptech.aptech4.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of an Admin product image upload")
public record ProductImageUploadResponse(
		@Schema(description = "Public relative path to assign on the product", example = "/uploads/products/9d1f7e4b-....webp")
		String imageUrl,
		@Schema(description = "Detected content type", example = "image/webp")
		String contentType,
		@Schema(description = "Stored file size in bytes")
		long sizeBytes
) {
}
