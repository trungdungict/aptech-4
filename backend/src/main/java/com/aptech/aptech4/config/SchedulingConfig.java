package com.aptech.aptech4.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Keeps {@code @Scheduled} jobs off WebSocket/STOMP heartbeat schedulers.
 * Without an explicit application scheduler, Boot may leave {@code @Scheduled} on whatever
 * {@link TaskScheduler} is present (including broker-related pools).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

	@Bean(name = "applicationTaskScheduler", destroyMethod = "shutdown")
	TaskScheduler applicationTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(2);
		scheduler.setThreadNamePrefix("app-sched-");
		scheduler.setRemoveOnCancelPolicy(true);
		scheduler.initialize();
		return scheduler;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setTaskScheduler(applicationTaskScheduler());
	}
}
