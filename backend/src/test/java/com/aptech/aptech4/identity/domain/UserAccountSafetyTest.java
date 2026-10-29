package com.aptech.aptech4.identity.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class UserAccountSafetyTest {

	@Test
	void toStringDoesNotExposePasswordHash() {
		UserAccount user = new UserAccount();
		user.setId(UUID.randomUUID());
		user.setEmail("customer@example.com");
		user.setPasswordHash("$2a$10$super-secret-hash-value");
		user.setFullName("Customer");

		String text = user.toString();

		assertFalse(text.contains("super-secret"));
		assertFalse(text.contains("password"));
		assertTrue(text.contains("customer@example.com"));
	}

	@Test
	void equalsUsesIdOnly() {
		UUID id = UUID.randomUUID();
		UserAccount a = new UserAccount();
		a.setId(id);
		a.setEmail("a@example.com");
		a.setPasswordHash("hash-a");

		UserAccount b = new UserAccount();
		b.setId(id);
		b.setEmail("b@example.com");
		b.setPasswordHash("hash-b");

		assertTrue(a.equals(b));
		assertTrue(a.hashCode() == b.hashCode());
	}

	@Test
	void refreshTokenToStringDoesNotExposeHash() {
		RefreshToken token = new RefreshToken();
		token.setId(UUID.randomUUID());
		token.setTokenHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

		String text = token.toString();

		assertFalse(text.contains("0123456789abcdef"));
		assertFalse(text.toLowerCase().contains("hash"));
	}
}
