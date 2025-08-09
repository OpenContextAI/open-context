package com.opencontext.config;

import org.springframework.context.annotation.Configuration;
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
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*") // Allow all origins in development environment
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS","PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

