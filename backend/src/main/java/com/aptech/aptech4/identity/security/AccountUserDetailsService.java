package com.aptech.aptech4.identity.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.identity.persistence.UserAccountRepository;

@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;

	public AccountUserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String email = EmailNormalizer.normalize(username);
		return userAccountRepository.findByEmailWithRoles(email)
			.map(AccountUserDetails::new)
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}
}
