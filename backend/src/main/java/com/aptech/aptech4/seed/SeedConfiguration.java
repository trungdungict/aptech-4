package com.aptech.aptech4.seed;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoSeedProperties.class)
public class SeedConfiguration {
}
