package com.aptech.aptech4.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Redis cache failures must not abort public catalog reads (ADR-015).
 */
class CacheErrorHandlerTest {

	@Test
	void redisFailuresAreSwallowed() {
		CacheConfig config = new CacheConfig();
		CacheErrorHandler handler = config.errorHandler();
		Cache cache = mock(Cache.class);
		when(cache.getName()).thenReturn("catalog:products:detail");
		RuntimeException redisDown = new RedisConnectionFailureException("redis down");

		assertThatCode(() -> handler.handleCacheGetError(redisDown, cache, "key")).doesNotThrowAnyException();
		assertThatCode(() -> handler.handleCachePutError(redisDown, cache, "key", "value")).doesNotThrowAnyException();
		assertThatCode(() -> handler.handleCacheEvictError(redisDown, cache, "key")).doesNotThrowAnyException();
		assertThatCode(() -> handler.handleCacheClearError(redisDown, cache)).doesNotThrowAnyException();
	}
}
