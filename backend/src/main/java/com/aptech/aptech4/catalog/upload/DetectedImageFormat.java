package com.aptech.aptech4.catalog.upload;

enum DetectedImageFormat {
	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private final String contentType;
	private final String extension;

	DetectedImageFormat(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}

	String contentType() {
		return contentType;
	}

	String extension() {
		return extension;
	}
}
