package com.aptech.aptech4.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs only when {@code aptech.seed.reset=true}. Not active on normal API startup.
 * Dry-run prints scope without deletes; destructive path requires {@code reset-confirm=yes}.
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "aptech.seed", name = "reset", havingValue = "true")
public class DemoResetRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoResetRunner.class);

	private final DemoResetService demoResetService;
	private final DemoSeedService demoSeedService;
	private final DemoSeedProperties properties;
	private final ConfigurableApplicationContext context;

	public DemoResetRunner(
			DemoResetService demoResetService,
			DemoSeedService demoSeedService,
			DemoSeedProperties properties,
			ConfigurableApplicationContext context
	) {
		this.demoResetService = demoResetService;
		this.demoSeedService = demoSeedService;
		this.properties = properties;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (properties.isEnabled()) {
			throw new IllegalStateException(
					"Refuse: aptech.seed.enabled and aptech.seed.reset both true — run seed-demo or reset-demo, not both"
			);
		}

		DemoResetPlan plan = demoResetService.plan();
		log.info(
				"Demo reset plan — jdbcUrl={} demoUsers={} demoCategories={} demoProducts={} orders={} carts={} reservations={}",
				plan.jdbcUrl(),
				plan.demoUsers(),
				plan.demoCategories(),
				plan.demoProducts(),
				plan.dependentOrders(),
				plan.dependentCarts(),
				plan.dependentReservations()
		);
		log.info("Scope emails={} slugs={} skus={}", plan.demoEmails(), plan.demoCategorySlugs(), plan.demoProductSkus());
		log.info(
				"This deletes ONLY rows matching those natural keys (+ their dependents). Not a database/volume wipe."
		);

		if (properties.isResetDryRun()) {
			log.info("Dry-run complete — no rows deleted.");
			exitIfRequested();
			return;
		}

		DemoResetResult result = demoResetService.execute(properties.getResetConfirm());
		log.info("Reset finished: {}", result);

		if (properties.isReseedAfterReset()) {
			DemoSeedResult seedResult = demoSeedService.seedAll();
			log.info("Reseed after reset: {}", seedResult);
		}
		else {
			log.info("Reseed skipped — run make seed-demo if you need fixtures again.");
		}

		exitIfRequested();
	}

	private void exitIfRequested() {
		if (properties.isExitAfter()) {
			int code = SpringApplication.exit(context, () -> 0);
			System.exit(code);
		}
	}
}
