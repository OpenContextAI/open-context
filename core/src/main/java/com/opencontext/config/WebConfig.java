package com.opencontext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web-related configuration.
 * Handles web-related settings such as CORS, interceptors, resource handlers, etc.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * CORS configuration.
     * Allows CORS for communication with frontend in development environment.
     */
    /**
     * RestTemplate bean for HTTP client operations.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

