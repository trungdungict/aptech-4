package com.aptech.aptech4.identity.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = header.substring(7).trim();
		if (token.isEmpty()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			JwtService.AccessTokenPrincipal principal = jwtService.parseAccessToken(token);
			var authentication = new UsernamePasswordAuthenticationToken(
					principal,
					null,
					SecurityRoles.authorities(principal.roles())
			);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (JwtException | IllegalArgumentException ex) {
			// Do not log the token or Authorization header.
			log.debug("Rejecting bearer token: {}", ex.getClass().getSimpleName());
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}
}
