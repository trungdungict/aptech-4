package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.security.CurrentUser;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;

/**
 * Proves tool callbacks see the captured JWT identity even when the calling thread
 * has a cleared SecurityContext (Reactor {@code boundedElastic} / SSE tool hop).
 */
class ChatToolSecuritySupportTest {

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void restoresCapturedIdentityOnWorkerThreadWithoutContext() throws Exception {
		UUID customerId = UUID.randomUUID();
		SecurityContext captured = authenticatedContext(customerId, "cust-a@example.com");
		SecurityContextHolder.setContext(captured);

		IdentityProbeTools probe = new IdentityProbeTools();
		ChatToolSecuritySupport.SecuredTools<IdentityProbeTools> secured =
				ChatToolSecuritySupport.secure(probe, captured);

		SecurityContextHolder.clearContext();
		assertThatThrownBy(CurrentUser::requireUserId)
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.UNAUTHORIZED);

		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<UUID> seen = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();

		Thread worker = new Thread(() -> {
			try {
				SecurityContextHolder.clearContext();
				seen.set(UUID.fromString(secured.tools().whoAmI()));
			}
			catch (Throwable t) {
				error.set(t);
			}
			finally {
				done.countDown();
			}
		}, "simulated-boundedElastic");
		worker.start();
		assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(error.get()).isNull();
		assertThat(seen.get()).isEqualTo(customerId);
		assertThat(secured.authFailure().get()).isNull();
	}

	@Test
	void mapsMissingAuthenticationOnToolToToolAuthorizationException() {
		UUID customerId = UUID.randomUUID();
		SecurityContext captured = authenticatedContext(customerId, "cust-a@example.com");
		// Capture was valid at secure() time; empty context on the worker is restored from capture.
		IdentityProbeTools probe = new IdentityProbeTools();
		ChatToolSecuritySupport.SecuredTools<IdentityProbeTools> secured =
				ChatToolSecuritySupport.secure(probe, captured);

		// Replace capture by wrapping a tools object that clears and re-checks without restore —
		// instead exercise unauthorized from CurrentUser when secure() itself has no auth:
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> ChatToolSecuritySupport.secure(probe, SecurityContextHolder.getContext()))
				.isInstanceOf(ToolAuthorizationException.class)
				.hasMessageContaining("Authentication required");
	}

	@Test
	void mapsUnauthorizedApiExceptionToToolAuthorizationAndRecordsFailure() {
		UUID customerId = UUID.randomUUID();
		SecurityContext captured = authenticatedContext(customerId, "cust-a@example.com");
		UnauthorizedTools tools = new UnauthorizedTools();
		ChatToolSecuritySupport.SecuredTools<UnauthorizedTools> secured =
				ChatToolSecuritySupport.secure(tools, captured);

		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> secured.tools().needsAuth())
				.isInstanceOf(ToolAuthorizationException.class);
		assertThat(secured.authFailure().get()).isInstanceOf(ToolAuthorizationException.class);
	}

	@Test
	void doesNotTreatForbiddenAsToolAuthorization() {
		UUID customerId = UUID.randomUUID();
		SecurityContext captured = authenticatedContext(customerId, "cust-a@example.com");
		ForbiddenTools tools = new ForbiddenTools();
		ChatToolSecuritySupport.SecuredTools<ForbiddenTools> secured =
				ChatToolSecuritySupport.secure(tools, captured);

		assertThatThrownBy(() -> secured.tools().denied())
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(secured.authFailure().get()).isNull();
	}

	private static SecurityContext authenticatedContext(UUID userId, String email) {
		var principal = new JwtService.AccessTokenPrincipal(userId, email, List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(auth);
		return context;
	}

	public static class IdentityProbeTools {
		@Tool(description = "Return authenticated user id")
		public String whoAmI() {
			return CurrentUser.requireUserId().toString();
		}
	}

	public static class UnauthorizedTools {
		@Tool(description = "Always unauthorized")
		public String needsAuth() {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
		}
	}

	public static class ForbiddenTools {
		@Tool(description = "Always forbidden")
		public String denied() {
			throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Administrators only");
		}
	}
}
