package com.aptech.aptech4.common.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String error;
	private final String messageKey;
	private final Object[] messageArgs;

	/**
	 * Legacy constructor: {@code message} is returned as-is (typically English).
	 * Prefer {@link #keyed(HttpStatus, String, String, Object...)} for localized messages.
	 */
	public ApiException(HttpStatus status, String error, String message) {
		super(message);
		this.status = status;
		this.error = error;
		this.messageKey = null;
		this.messageArgs = new Object[0];
	}

	private ApiException(HttpStatus status, String error, String messageKey, Object[] messageArgs, String fallbackMessage) {
		super(fallbackMessage);
		this.status = status;
		this.error = error;
		this.messageKey = messageKey;
		this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
	}

	public static ApiException keyed(HttpStatus status, String error, String messageKey, Object... args) {
		return new ApiException(status, error, messageKey, args, messageKey);
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public String getMessageKey() {
		return messageKey;
	}

	public Object[] getMessageArgs() {
		return messageArgs.clone();
	}
}
