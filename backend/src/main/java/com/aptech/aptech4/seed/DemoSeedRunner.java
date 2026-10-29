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
 * Runs only when {@code aptech.seed.enabled=true} (explicit Makefile / CLI). Not active by default.
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "aptech.seed", name = "enabled", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

	private final DemoSeedService demoSeedService;
	private final DemoSeedProperties properties;
	private final ConfigurableApplicationContext context;

	public DemoSeedRunner(
			DemoSeedService demoSeedService,
			DemoSeedProperties properties,
			ConfigurableApplicationContext context
	) {
		this.demoSeedService = demoSeedService;
		this.properties = properties;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (properties.isReset()) {
			throw new IllegalStateException(
					"Refuse: aptech.seed.enabled and aptech.seed.reset both true — run seed-demo or reset-demo, not both"
			);
		}
		log.info("aptech.seed.enabled=true — starting deterministic demo seed");
		DemoSeedResult result = demoSeedService.seedAll();
		log.info("Seed finished: {}", result);
		if (properties.isExitAfter()) {
			int code = SpringApplication.exit(context, () -> 0);
			System.exit(code);
		}
	}
}
