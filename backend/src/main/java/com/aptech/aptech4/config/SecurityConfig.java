package com.aptech.aptech4.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.aptech.aptech4.catalog.config.InventoryProperties;
import com.aptech.aptech4.identity.security.CorsProperties;
import com.aptech.aptech4.order.payment.MockPaymentProperties;
import com.aptech.aptech4.identity.security.JwtAuthenticationFilter;
import com.aptech.aptech4.identity.security.JwtProperties;
import com.aptech.aptech4.identity.security.RestAccessDeniedHandler;
import com.aptech.aptech4.identity.security.RestAuthenticationEntryPoint;

/**
 * Stateless Bearer-JWT API security (ADR-005).
 * CSRF is disabled because clients authenticate via Authorization header, not cookies.
 * Deny-by-default: public allow-list, then {@code /api/v1/admin/**} requires {@code ROLE_ADMIN},
 * everything else authenticated. Method security ({@code @PreAuthorize}) adds finer checks.
 *
 * <p>{@link RequestAttributeSecurityContextRepository} keeps the authenticated
 * {@code SecurityContext} on the HTTP request so Tomcat async dispatch (SSE / {@code SseEmitter})
 * can re-authorize without re-parsing the JWT. Without this, async completion hits
 * {@code AuthorizationFilter} with an empty context, fails with Access Denied on an already
 * committed {@code text/event-stream}, and browsers report {@code ERR_INCOMPLETE_CHUNKED_ENCODING}.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
		JwtProperties.class,
		CorsProperties.class,
		InventoryProperties.class,
		MockPaymentProperties.class
})
public class SecurityConfig {

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new RequestAttributeSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler,
			SecurityContextRepository securityContextRepository
	) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.securityContext(securityContext -> securityContext
					.securityContextRepository(securityContextRepository)
					.requireExplicitSave(true)
			)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
			)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(
					"/error",
					"/actuator/health",
					"/actuator/health/**",
					"/actuator/info",
					// Scraped by Prometheus on the Compose network / loopback; keep API_PUBLISH loopback-only.
					"/actuator/prometheus",
					"/api/v1/health",
					"/api/v1/auth/register",
					"/api/v1/auth/login",
					"/api/v1/auth/refresh",
					"/api/v1/auth/logout",
					"/v3/api-docs",
					"/v3/api-docs/**",
					"/swagger-ui.html",
					"/swagger-ui/**"
				).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/*").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/*").permitAll()
				// Handshake is public; STOMP CONNECT carries the Bearer JWT (ADR-018).
				.requestMatchers("/ws", "/ws/**").permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated()
			)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(corsProperties.originList());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
		config.setExposedHeaders(List.of("Authorization"));
		config.setAllowCredentials(corsProperties.isAllowCredentials());
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
