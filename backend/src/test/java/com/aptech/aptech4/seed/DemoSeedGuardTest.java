package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DemoSeedGuardTest {

	@Test
	void localProfileAllowed() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("local");
		assertThatCode(() -> DemoSeedGuard.assertAllowed(env, false)).doesNotThrowAnyException();
	}

	@Test
	void prodProfileRefusedUnlessAllowOnProd() {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("prod");
		assertThatThrownBy(() -> DemoSeedGuard.assertAllowed(env, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prod");
		assertThatCode(() -> DemoSeedGuard.assertAllowed(env, true)).doesNotThrowAnyException();
	}
}
