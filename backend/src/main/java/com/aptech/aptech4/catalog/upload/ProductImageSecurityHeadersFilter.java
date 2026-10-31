package com.aptech.aptech4.catalog.upload;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Hardens uploaded image responses: nosniff + no directory listing semantics for the public path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ProductImageSecurityHeadersFilter extends OncePerRequestFilter {

	private final ProductImageProperties properties;

	public ProductImageSecurityHeadersFilter(ProductImageProperties properties) {
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		String prefix = properties.getPublicPath();
		return path == null || !path.startsWith(prefix + "/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		response.setHeader("X-Content-Type-Options", "nosniff");
		filterChain.doFilter(request, response);
	}
}
