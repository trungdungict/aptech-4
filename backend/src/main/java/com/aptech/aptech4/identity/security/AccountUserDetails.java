package com.aptech.aptech4.identity.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.UserAccount;

public class AccountUserDetails implements UserDetails {

	private final UUID id;
	private final String email;
	private final String passwordHash;
	private final String fullName;
	private final boolean enabled;
	private final Collection<? extends GrantedAuthority> authorities;

	public AccountUserDetails(UserAccount account) {
		this.id = account.getId();
		this.email = account.getEmail();
		this.passwordHash = account.getPasswordHash();
		this.fullName = account.getFullName();
		this.enabled = account.isEnabled();
		this.authorities = SecurityRoles.authorities(
				account.getRoles().stream().map(Role::getCode).map(Enum::name).toList()
		);
	}

	public UUID getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public Collection<String> roleCodes() {
		return authorities.stream()
			.map(GrantedAuthority::getAuthority)
			.map(a -> a.startsWith(SecurityRoles.ROLE_PREFIX) ? a.substring(SecurityRoles.ROLE_PREFIX.length()) : a)
			.toList();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
