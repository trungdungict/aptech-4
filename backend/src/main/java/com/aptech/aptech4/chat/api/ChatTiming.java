package com.aptech.aptech4.chat.api;

/**
 * Optional latency breakdown for local diagnostics (ADR-019 verification).
 */
public record ChatTiming(
		Long toolMs,
		Long inferenceMs,
		Long totalMs,
		String path
) {
}
