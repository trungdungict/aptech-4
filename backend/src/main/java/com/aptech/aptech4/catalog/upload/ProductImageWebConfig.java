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
		Path dir = properties.uploadDirectory();
		String location = dir.toUri().toString();
		if (!location.endsWith("/")) {
			location = location + "/";
		}
		String pattern = properties.getPublicPath() + "/**";
		registry.addResourceHandler(pattern)
				.addResourceLocations(location)
				.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
				.resourceChain(true);
	}
}
