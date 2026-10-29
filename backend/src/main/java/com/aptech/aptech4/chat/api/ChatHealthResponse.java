package com.aptech.aptech4.chat.api;

public record ChatHealthResponse(
		boolean enabled,
		boolean modelConfigured,
		String model,
		String status,
		String detail
) {
}
