package com.aptech.aptech4.identity.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.security.JwtService.AccessTokenPrincipal;

/**
 * Resolves the authenticated access-token principal from the security context.
 * Never trust client-supplied user ids for ownership.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static AccessTokenPrincipal requirePrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AccessTokenPrincipal principal)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
		}
		return principal;
	}

	public static UUID requireUserId() {
		return requirePrincipal().userId();
	}
}
