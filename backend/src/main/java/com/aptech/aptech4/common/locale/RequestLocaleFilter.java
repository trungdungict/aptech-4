package com.aptech.aptech4.common.locale;

import java.io.IOException;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolves {@code Accept-Language} once per request and exposes {@link AppLocale} via
 * {@link RequestLocaleHolder} and Spring {@link LocaleContextHolder}. Controllers must not parse
 * the header themselves — call {@link RequestLocaleHolder#get()}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLocaleFilter extends OncePerRequestFilter {

	public static final String REQUEST_ATTRIBUTE = AppLocale.class.getName();

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		AppLocale locale = AcceptLanguageParser.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
		RequestLocaleHolder.set(locale);
		request.setAttribute(REQUEST_ATTRIBUTE, locale);
		LocaleContextHolder.setLocale(locale.toJavaLocale(), true);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			RequestLocaleHolder.clear();
			LocaleContextHolder.resetLocaleContext();
		}
	}
}
