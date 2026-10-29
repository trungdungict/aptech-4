package com.aptech.aptech4.chat.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured tool outcome so callers (and the model) can distinguish empty success from failure.
 * Failures must never be coerced into empty item lists.
 */
public final class ChatToolEnvelope {

	public static final String SUCCESS_NONEMPTY = "SUCCESS_NONEMPTY";
	public static final String SUCCESS_EMPTY = "SUCCESS_EMPTY";
	public static final String FAILURE = "FAILURE";

	public static final String ERROR_VALIDATION = "VALIDATION";
	public static final String ERROR_AUTHORIZATION = "AUTHORIZATION";
	public static final String ERROR_DATA_ACCESS = "DATA_ACCESS";
	public static final String ERROR_INTERNAL = "INTERNAL";

	private ChatToolEnvelope() {
	}

	public static Map<String, Object> success(String tool, Object result, int resultSize, long durationMs) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("tool", tool);
		body.put("status", resultSize > 0 ? SUCCESS_NONEMPTY : SUCCESS_EMPTY);
		body.put("resultSize", resultSize);
		body.put("durationMs", durationMs);
		body.put("result", result);
		if (resultSize == 0) {
			body.put(
					"message",
					"Tool succeeded with zero matching rows. Do not invent SKUs, names, or quantities."
			);
		}
		return body;
	}

	public static Map<String, Object> failure(String tool, String errorCategory, String detail, long durationMs) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("tool", tool);
		body.put("status", FAILURE);
		body.put("resultSize", null);
		body.put("durationMs", durationMs);
		body.put("errorCategory", errorCategory);
		body.put("error", detail);
		body.put("result", null);
		body.put(
				"message",
				"Tool failed (" + errorCategory + "). Do not invent data; tell the user the query could not be completed."
		);
		return body;
	}
}
