package com.aptech.aptech4.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.aptech.aptech4.catalog.cache.CatalogCacheNames;
import com.aptech.aptech4.catalog.cache.CatalogCacheProperties;

/**
 * Redis-backed Spring Cache for public catalog (ADR-015).
 * Cache errors are logged and swallowed so PostgreSQL remains the source of truth.
 * Values use JDK serialization (DTOs are {@code Serializable} records).
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CacheConfig implements CachingConfigurer {

	private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

	@Bean
	RedisCacheManager cacheManager(
			RedisConnectionFactory connectionFactory,
			CatalogCacheProperties catalogCacheProperties
	) {
		JdkSerializationRedisSerializer valueSerializer = new JdkSerializationRedisSerializer();

		RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
				.disableCachingNullValues()
				.serializeKeysWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new StringRedisSerializer()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(valueSerializer));

		Duration categoriesTtl = Duration.ofSeconds(Math.max(1, catalogCacheProperties.getCategoriesTtlSeconds()));
		Duration productsTtl = Duration.ofSeconds(Math.max(1, catalogCacheProperties.getProductsTtlSeconds()));

		return RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(base.entryTtl(productsTtl))
				.withCacheConfiguration(CatalogCacheNames.CATEGORIES_LIST, base.entryTtl(categoriesTtl))
				.withCacheConfiguration(CatalogCacheNames.CATEGORIES_DETAIL, base.entryTtl(categoriesTtl))
				.withCacheConfiguration(CatalogCacheNames.PRODUCTS_LIST, base.entryTtl(productsTtl))
				.withCacheConfiguration(CatalogCacheNames.PRODUCTS_DETAIL, base.entryTtl(productsTtl))
				.build();
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new SimpleCacheErrorHandler() {
			@Override
			public void handleCacheGetError(RuntimeException exception, org.springframework.cache.Cache cache, Object key) {
				log.warn("Catalog cache GET failed cache={} — falling back to database", cache.getName());
			}

			@Override
			public void handleCachePutError(
					RuntimeException exception,
					org.springframework.cache.Cache cache,
					Object key,
					Object value
			) {
				log.warn("Catalog cache PUT failed cache={} — response still served from database", cache.getName());
			}

			@Override
			public void handleCacheEvictError(
					RuntimeException exception,
					org.springframework.cache.Cache cache,
					Object key
			) {
				log.warn("Catalog cache EVICT failed cache={} — TTL remains the safety net", cache.getName());
			}

			@Override
			public void handleCacheClearError(RuntimeException exception, org.springframework.cache.Cache cache) {
				log.warn("Catalog cache CLEAR failed cache={} — TTL remains the safety net", cache.getName());
			}
		};
	}
}
