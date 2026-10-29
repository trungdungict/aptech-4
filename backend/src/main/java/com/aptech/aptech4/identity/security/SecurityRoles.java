package com.aptech.aptech4.identity.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.aptech.aptech4.identity.domain.RoleCode;

/**
 * Maps domain {@link RoleCode} to Spring authorities with a consistent {@code ROLE_} prefix.
 */
public final class SecurityRoles {

	public static final String ROLE_PREFIX = "ROLE_";

	private SecurityRoles() {
	}

	public static String authority(RoleCode role) {
		return ROLE_PREFIX + role.name();
	}

	public static String authority(String roleCode) {
		String normalized = roleCode.startsWith(ROLE_PREFIX) ? roleCode.substring(ROLE_PREFIX.length()) : roleCode;
		return ROLE_PREFIX + normalized;
	}

	public static Set<GrantedAuthority> authorities(Collection<String> roleCodes) {
		return roleCodes.stream()
			.map(SecurityRoles::authority)
			.map(SimpleGrantedAuthority::new)
			.collect(Collectors.toUnmodifiableSet());
	}
}
