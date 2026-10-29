package com.aptech.aptech4.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.aptech.aptech4.notification.ws.StompJwtChannelInterceptor;

/**
 * STOMP over WebSocket at {@code /ws} (ADR-018). JWT is validated on CONNECT, not via URL.
 *
 * <p>Heartbeats require an explicit {@link TaskScheduler} on the simple broker; without them
 * Spring advertises {@code heart-beat:0,0}, half-open sessions linger, and the first push to a
 * dead session can block an outbound channel thread up to the transport {@code sendTimeLimit}
 * (Spring default 10_000 ms) before the session is closed — matching intermittent ~10s UI delay.
 *
 * <p>{@code @Order(1)} runs after Boot's WebSocket channel auto-config ({@code Ordered=0}) so our
 * dedicated channel executors replace the shared {@code applicationTaskExecutor}.
 */
@Configuration
@EnableWebSocketMessageBroker
@Order(1)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** Server/client heartbeat interval (ms); must stay aligned with the SPA STOMP client. */
	public static final long[] BROKER_HEARTBEAT_MS = { 10_000L, 10_000L };

	private final StompJwtChannelInterceptor stompJwtChannelInterceptor;

	public WebSocketConfig(StompJwtChannelInterceptor stompJwtChannelInterceptor) {
		this.stompJwtChannelInterceptor = stompJwtChannelInterceptor;
	}

	/**
	 * Dedicated heartbeat scheduler — do not inject {@code messageBrokerTaskScheduler}
	 * (that bean is created by the same WebSocket configurer pipeline and causes a cycle).
	 */
	@Bean(name = "stompBrokerHeartbeatScheduler", destroyMethod = "shutdown")
	static TaskScheduler stompBrokerHeartbeatScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
		scheduler.setThreadNamePrefix("stomp-hb-");
		scheduler.setRemoveOnCancelPolicy(true);
		scheduler.initialize();
		return scheduler;
	}

	@Bean(name = "stompInboundChannelExecutor", destroyMethod = "shutdown")
	ThreadPoolTaskExecutor stompInboundChannelExecutor() {
		return buildChannelExecutor("stomp-in-");
	}

	@Bean(name = "stompOutboundChannelExecutor", destroyMethod = "shutdown")
	ThreadPoolTaskExecutor stompOutboundChannelExecutor() {
		return buildChannelExecutor("stomp-out-");
	}

	private static ThreadPoolTaskExecutor buildChannelExecutor(String namePrefix) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(namePrefix);
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(16);
		executor.setQueueCapacity(1000);
		executor.setAllowCoreThreadTimeOut(true);
		executor.initialize();
		return executor;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue", "/topic")
				.setTaskScheduler(stompBrokerHeartbeatScheduler())
				.setHeartbeatValue(BROKER_HEARTBEAT_MS);
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Handshake CORS is permissive; authorization is JWT on STOMP CONNECT (not cookies).
		// REST CORS remains driven by CorsProperties.
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompJwtChannelInterceptor);
		registration.executor(stompInboundChannelExecutor());
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.executor(stompOutboundChannelExecutor());
	}
}
