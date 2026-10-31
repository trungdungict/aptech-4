package com.aptech.aptech4.catalog.upload;

import java.nio.file.Path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ProductImageProperties.class)
public class ProductImageWebConfig implements WebMvcConfigurer {

	private final ProductImageProperties properties;

	public ProductImageWebConfig(ProductImageProperties properties) {
		this.properties = properties;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		register(registry, properties.getPublicPath(), properties.uploadDirectory());
		register(registry, properties.getPromotionPublicPath(), properties.promotionUploadDirectory());
	}

	private static void register(ResourceHandlerRegistry registry, String publicPath, Path dir) {
		String location = dir.toUri().toString();
		if (!location.endsWith("/")) {
			location = location + "/";
		}
		registry.addResourceHandler(publicPath + "/**")
				.addResourceLocations(location)
				.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
				.resourceChain(true);
	}
}
