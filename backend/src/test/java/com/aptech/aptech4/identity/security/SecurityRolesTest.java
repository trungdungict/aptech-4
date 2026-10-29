package com.aptech.aptech4.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import com.aptech.aptech4.identity.domain.RoleCode;

class SecurityRolesTest {

	@Test
	void authorityDoesNotDoublePrefix() {
		assertThat(SecurityRoles.authority(RoleCode.ADMIN)).isEqualTo("ROLE_ADMIN");
		assertThat(SecurityRoles.authority("ADMIN")).isEqualTo("ROLE_ADMIN");
		assertThat(SecurityRoles.authority("ROLE_ADMIN")).isEqualTo("ROLE_ADMIN");
		assertThat(SecurityRoles.authority("ROLE_CUSTOMER")).isEqualTo("ROLE_CUSTOMER");
	}

	@Test
	void authoritiesFromJwtRoleCodes() {
		Set<GrantedAuthority> authorities = SecurityRoles.authorities(List.of("CUSTOMER", "ADMIN"));
		assertThat(authorities)
			.extracting(GrantedAuthority::getAuthority)
			.containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
	}
}
