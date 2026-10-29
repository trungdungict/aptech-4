package com.aptech.aptech4.notification.ws;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;

import io.jsonwebtoken.JwtException;

/**
 * Authenticates STOMP CONNECT with the access JWT (Authorization: Bearer).
 * Rejects unauthorized subscriptions; never trusts client-supplied recipient ids.
 */
@Component
public class StompJwtChannelInterceptor implements ChannelInterceptor {

	private static final Logger log = LoggerFactory.getLogger(StompJwtChannelInterceptor.class);

	private final JwtService jwtService;

	public StompJwtChannelInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		StompCommand command = accessor.getCommand();
		if (StompCommand.CONNECT.equals(command)) {
			authenticateConnect(accessor);
			return message;
		}

		if (StompCommand.SUBSCRIBE.equals(command)) {
			assertAuthenticated(accessor);
			assertAllowedSubscription(accessor);
			return message;
		}

		if (StompCommand.SEND.equals(command)) {
			// Clients must not publish into notification queues.
			throw new IllegalArgumentException("Client SEND is not allowed");
		}

		return message;
	}

	private void authenticateConnect(StompHeaderAccessor accessor) {
		String header = firstHeader(accessor, HttpHeaders.AUTHORIZATION);
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			throw new IllegalArgumentException("Missing Bearer access token");
		}
		String token = header.substring(7).trim();
		if (token.isEmpty()) {
			throw new IllegalArgumentException("Empty Bearer access token");
		}
		try {
			JwtService.AccessTokenPrincipal principal = jwtService.parseAccessToken(token);
			Authentication authentication = new UsernamePasswordAuthenticationToken(
					principal,
					null,
					SecurityRoles.authorities(principal.roles())
			);
			accessor.setUser(authentication);
		}
		catch (JwtException | IllegalArgumentException ex) {
			log.debug("STOMP CONNECT rejected: {}", ex.getClass().getSimpleName());
			throw new IllegalArgumentException("Invalid or expired access token");
		}
	}

	private static void assertAuthenticated(StompHeaderAccessor accessor) {
		if (accessor.getUser() == null) {
			throw new IllegalArgumentException("Unauthenticated STOMP session");
		}
	}

	private static void assertAllowedSubscription(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (destination == null || !NotificationDestinations.USER_QUEUE.equals(destination)) {
			throw new IllegalArgumentException("Subscription not allowed");
		}
	}

	@Nullable
	private static String firstHeader(StompHeaderAccessor accessor, String name) {
		List<String> values = accessor.getNativeHeader(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		return values.getFirst();
	}
}
