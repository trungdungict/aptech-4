package com.aptech.aptech4.chat.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;

/**
 * Chat infrastructure. {@code ChatClient} is built per turn from {@code ChatModel}
 * inside {@link com.aptech.aptech4.chat.application.ChatService} — a
 * {@code @ConditionalOnBean(ChatModel)} factory here races Ollama autoconfig and
 * permanently skips the bean (Spring Boot evaluates the condition too early).
 *
 * <p>The inference executor wraps {@link DelegatingSecurityContextExecutor} so SSE
 * worker threads keep the caller's JWT authentication (CurrentUser + {@code @PreAuthorize}).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class ChatAiConfiguration {

	@Bean(name = "chatInferenceExecutor")
	Executor chatInferenceExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("chat-ai-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setAllowCoreThreadTimeOut(true);
		executor.initialize();
		return new DelegatingSecurityContextExecutor(executor);
	}
}
