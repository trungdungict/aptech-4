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
 * Hardens uploaded image responses: nosniff for product and promotion public paths.
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
		if (path == null) {
			return true;
		}
		String productPrefix = properties.getPublicPath() + "/";
		String promotionPrefix = properties.getPromotionPublicPath() + "/";
		return !path.startsWith(productPrefix) && !path.startsWith(promotionPrefix);
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
