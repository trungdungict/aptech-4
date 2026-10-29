package com.aptech.aptech4.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

	@Test
	void sha256HexIsDeterministicAndLength64() {
		String hash = RefreshTokenHasher.sha256Hex("sample-refresh-jwt");
		assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
		assertThat(RefreshTokenHasher.sha256Hex("sample-refresh-jwt")).isEqualTo(hash);
		assertThat(RefreshTokenHasher.sha256Hex("other")).isNotEqualTo(hash);
	}
}
