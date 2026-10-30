package com.aptech.aptech4.chat.application;

import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Per-turn CGLIB proxies that restore the caller's {@link SecurityContext} on every tool
 * method invocation — including Spring AI callbacks scheduled on Reactor {@code boundedElastic}
 * threads during SSE streaming.
 *
 * <p>Does not accept identity from prompts; tools still use {@code CurrentUser} / JWT only.
 * Unauthorized tool access becomes {@link ToolAuthorizationException}. A shared
 * {@link AtomicReference} records the failure so ChatService can fail closed even if
 * Spring AI swallows the exception into a tool observation (which would otherwise become
 * an LLM apology about JWT).
 */
public final class ChatToolSecuritySupport {

	private ChatToolSecuritySupport() {
	}

	public record SecuredTools<T>(T tools, AtomicReference<ToolAuthorizationException> authFailure) {
	}

	@SuppressWarnings("unchecked")
	public static <T> SecuredTools<T> secure(T tools, SecurityContext securityContext) {
		if (tools == null) {
			throw new IllegalArgumentException("tools must not be null");
		}
		AtomicReference<ToolAuthorizationException> authFailure = new AtomicReference<>();
		if (securityContext == null || securityContext.getAuthentication() == null) {
			ToolAuthorizationException missing = new ToolAuthorizationException(
					"Authentication required for chat tools"
			);
			authFailure.set(missing);
			throw missing;
		}
		ProxyFactory factory = new ProxyFactory(tools);
		factory.setProxyTargetClass(true);
		factory.addAdvice((MethodInterceptor) invocation -> {
			SecurityContext previous = SecurityContextHolder.getContext();
			try {
				SecurityContextHolder.setContext(securityContext);
				return invocation.proceed();
			}
			catch (ApiException ex) {
				throw recordAndRethrow(authFailure, unauthorizedOrRethrow(ex));
			}
			catch (Exception ex) {
				ApiException api = findUnauthorized(ex);
				if (api != null) {
					throw recordAndRethrow(authFailure, unauthorizedOrRethrow(api));
				}
				if (ex instanceof ToolAuthorizationException tae) {
					authFailure.compareAndSet(null, tae);
				}
				throw ex;
			}
			finally {
				SecurityContextHolder.setContext(previous);
			}
		});
		T proxy = (T) factory.getProxy(tools.getClass().getClassLoader());
		return new SecuredTools<>(proxy, authFailure);
	}

	private static RuntimeException recordAndRethrow(
			AtomicReference<ToolAuthorizationException> authFailure,
			RuntimeException ex
	) {
		if (ex instanceof ToolAuthorizationException tae) {
			authFailure.compareAndSet(null, tae);
		}
		return ex;
	}

	private static RuntimeException unauthorizedOrRethrow(ApiException ex) {
		if (ex.getStatus() == HttpStatus.UNAUTHORIZED) {
			return new ToolAuthorizationException(
					ex.getMessage() == null ? "Authentication required" : ex.getMessage(),
					ex
			);
		}
		return ex;
	}

	private static ApiException findUnauthorized(Throwable ex) {
		Throwable cursor = ex;
		while (cursor != null) {
			if (cursor instanceof ApiException api && api.getStatus() == HttpStatus.UNAUTHORIZED) {
				return api;
			}
			if (cursor instanceof ToolAuthorizationException) {
				return null;
			}
			Throwable next = cursor.getCause();
			if (next == cursor) {
				break;
			}
			cursor = next;
		}
		return null;
	}
}
