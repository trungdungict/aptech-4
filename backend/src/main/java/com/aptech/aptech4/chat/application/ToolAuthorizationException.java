package com.aptech.aptech4.chat.application;

/**
 * Raised when a chat tool cannot run because the authenticated SecurityContext is missing.
 * ChatService maps this to a fixed "Sign in required" reply — never an LLM apology about JWT.
 */
public final class ToolAuthorizationException extends RuntimeException {

	public ToolAuthorizationException(String message) {
		super(message);
	}

	public ToolAuthorizationException(String message, Throwable cause) {
		super(message, cause);
	}
}
