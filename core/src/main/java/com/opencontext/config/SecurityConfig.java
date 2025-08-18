package com.opencontext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security configuration for OpenContext API.
 * Implements API Key authentication for Admin APIs and allows unrestricted access
 * to MCP APIs and development endpoints.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Environment environment;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security with profile-specific rules");
        
        http
            // Disable CSRF for REST API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS for frontend integration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable form login and basic auth
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            
            // Stateless session management for REST API
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Allow health check endpoints
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                
                // Allow Swagger UI and API documentation (development)
                .requestMatchers(
                    "/swagger-ui/**", 
                    "/v3/api-docs/**", 
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                
                // Allow MCP API endpoints (no authentication required per PRD)
                .requestMatchers("/api/v1/search/**", "/api/v1/get-content/**").permitAll()
                
                // Allow development mock data endpoints (development only)
                .requestMatchers("/api/v1/dev/**").permitAll()
                
                // Admin APIs require API Key authentication
                .requestMatchers("/api/v1/sources/**").permitAll() // Authentication handled by filter
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );
        
        // Add API Key authentication filter for Admin APIs
        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        log.info("Security configuration completed successfully");
        return http.build();
    }

    /**
     * CORS configuration for frontend integration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*"); // Allow all origins in development
        configuration.addAllowedMethod("*"); // Allow all HTTP methods
        configuration.addAllowedHeader("*"); // Allow all headers
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        log.info("CORS configuration applied to /api/** endpoints");
        return source;
    }
}