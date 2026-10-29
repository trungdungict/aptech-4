package com.aptech.aptech4.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

	@Test
	void normalizesTrimAndLowerCase() {
		assertEquals("user@example.com", EmailNormalizer.normalize("  User@Example.COM "));
	}
}
