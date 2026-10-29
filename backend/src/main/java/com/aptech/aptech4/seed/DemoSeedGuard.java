package com.aptech.aptech4.seed;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class DemoSeedGuard {

	private DemoSeedGuard() {
	}

	static void assertAllowed(Environment environment, boolean allowOnProd) {
		if (environment.acceptsProfiles(Profiles.of("prod")) && !allowOnProd) {
			throw new IllegalStateException(
					"Demo seed/reset refused: active profile includes 'prod'. Use local/demo only (see docs/DEMO_SEED.md)."
			);
		}
	}
}
