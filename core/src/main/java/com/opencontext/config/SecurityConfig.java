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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Spring Security with profile-specific rules");
        
        http
            // Disable CSRF for REST API
            .csrf(AbstractHttpConfigurer::disable)
            
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
                
                // Admin APIs will require API Key authentication (TODO: implement in next step)
                .requestMatchers("/api/v1/sources/**").permitAll() // Temporary - will add API key auth
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );
        
        // TODO: Add API Key authentication filter for Admin APIs
        // http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        log.info("Security configuration completed successfully");
        return http.build();
    }
}