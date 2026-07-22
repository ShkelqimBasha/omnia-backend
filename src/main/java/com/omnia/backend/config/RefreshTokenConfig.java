package com.omnia.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(
        RefreshTokenProperties.class
)
public class RefreshTokenConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}