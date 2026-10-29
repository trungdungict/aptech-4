package com.aptech.aptech4.common.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
		return ResponseEntity.status(ex.getStatus())
			.body(ApiError.of(ex.getStatus().value(), ex.getError(), ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
			.map(GlobalExceptionHandler::formatFieldError)
			.toList();
		return ResponseEntity.badRequest()
			.body(ApiError.of(400, "Bad Request", "Validation failed", request.getRequestURI(), details));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		String name = ex.getName() == null ? "parameter" : ex.getName();
		return ResponseEntity.badRequest()
			.body(ApiError.of(400, "Bad Request", "Invalid value for '" + name + "'", request.getRequestURI()));
	}

	@ExceptionHandler({ BadCredentialsException.class, DisabledException.class })
	ResponseEntity<ApiError> handleBadCredentials(AuthenticationException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiError.of(401, "Unauthorized", "Invalid email or password", request.getRequestURI()));
	}

	private static String formatFieldError(FieldError error) {
		return error.getField() + ": " + error.getDefaultMessage();
	}
}
