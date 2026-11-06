package com.aptech.aptech4.common.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

class RequestLocaleFilterTest {

	private final RequestLocaleFilter filter = new RequestLocaleFilter();

	@AfterEach
	void clearThreadState() {
		RequestLocaleHolder.clear();
		LocaleContextHolder.resetLocaleContext();
	}

	@Test
	void bindsLocaleDuringRequestAndClearsAfterwards() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
		MockHttpServletResponse response = new MockHttpServletResponse();

		AtomicReference<AppLocale> seenInChain = new AtomicReference<>();
		FilterChain chain = (req, res) -> {
			seenInChain.set(RequestLocaleHolder.get());
			assertThat(req.getAttribute(RequestLocaleFilter.REQUEST_ATTRIBUTE)).isEqualTo(AppLocale.EN);
			assertThat(LocaleContextHolder.getLocale().getLanguage()).isEqualTo("en");
		};

		filter.doFilter(request, response, chain);

		assertThat(seenInChain.get()).isEqualTo(AppLocale.EN);
		assertThat(RequestLocaleHolder.getOrNull()).isNull();
		assertThat(RequestLocaleHolder.get()).isEqualTo(AppLocale.VI);
	}

	@Test
	void missingHeaderBindsVietnamese() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AtomicReference<AppLocale> seen = new AtomicReference<>();

		filter.doFilter(request, response, (req, res) -> seen.set(RequestLocaleHolder.get()));

		assertThat(seen.get()).isEqualTo(AppLocale.VI);
		assertThat(RequestLocaleHolder.getOrNull()).isNull();
	}

	@Test
	void clearsHolderWhenChainThrows() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en");
		MockHttpServletResponse response = new MockHttpServletResponse();

		try {
			filter.doFilter(request, response, (req, res) -> {
				assertThat(RequestLocaleHolder.get()).isEqualTo(AppLocale.EN);
				throw new IllegalStateException("boom");
			});
		}
		catch (IllegalStateException ignored) {
			// expected
		}

		assertThat(RequestLocaleHolder.getOrNull()).isNull();
	}

	@Test
	void concurrentRequestsDoNotLeakLocale() throws Exception {
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<AppLocale>> futures = new ArrayList<>();

		try {
			for (int i = 0; i < threads; i++) {
				AppLocale expected = (i % 2 == 0) ? AppLocale.EN : AppLocale.VI;
				String header = expected == AppLocale.EN ? "en-GB" : "vi-VN";
				futures.add(pool.submit(() -> {
					MockHttpServletRequest request = new MockHttpServletRequest();
					request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, header);
					MockHttpServletResponse response = new MockHttpServletResponse();
					AtomicReference<AppLocale> seen = new AtomicReference<>();
					FilterChain chain = mock(FilterChain.class);
					doAnswer(invocation -> {
						ready.countDown();
						assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
						seen.set(RequestLocaleHolder.get());
						// Yield so other threads exercise the same ThreadLocal class.
						Thread.yield();
						assertThat(RequestLocaleHolder.get()).isEqualTo(seen.get());
						return null;
					}).when(chain).doFilter(any(), any());

					filter.doFilter(request, response, chain);
					assertThat(RequestLocaleHolder.getOrNull()).isNull();
					return seen.get();
				}));
			}

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			go.countDown();

			for (int i = 0; i < threads; i++) {
				AppLocale expected = (i % 2 == 0) ? AppLocale.EN : AppLocale.VI;
				assertThat(futures.get(i).get(5, TimeUnit.SECONDS)).isEqualTo(expected);
			}
		}
		finally {
			pool.shutdownNow();
		}
	}
}
