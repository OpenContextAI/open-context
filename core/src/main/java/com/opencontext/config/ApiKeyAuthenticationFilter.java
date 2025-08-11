package com.opencontext.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencontext.common.CommonResponse;
import com.opencontext.enums.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter for API Key authentication on admin endpoints.
 * 
 * This filter validates the X-API-KEY header for endpoints that require
 * admin access, specifically the document management APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${opencontext.api.key:dev-api-key-123}")
    private String validApiKey;

    // Endpoints that require API Key authentication
    private static final List<String> PROTECTED_ENDPOINTS = Arrays.asList(
            "/api/v1/sources"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        
        // Check if this endpoint requires API Key authentication
        boolean requiresAuth = PROTECTED_ENDPOINTS.stream()
                .anyMatch(requestPath::startsWith);

        if (requiresAuth) {
            String apiKey = request.getHeader("X-API-KEY");
            
            if (!StringUtils.hasText(apiKey)) {
                log.warn("API Key missing for protected endpoint: {}", requestPath);
                sendErrorResponse(response, ErrorCode.INSUFFICIENT_PERMISSION, 
                        "API Key is required. Please provide X-API-KEY header.");
                return;
            }

            if (!validApiKey.equals(apiKey)) {
                log.warn("Invalid API Key provided for endpoint: {}", requestPath);
                sendErrorResponse(response, ErrorCode.INSUFFICIENT_PERMISSION, 
                        "Invalid API Key provided.");
                return;
            }

            log.debug("API Key authentication successful for endpoint: {}", requestPath);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sends an error response in JSON format.
     */
    private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode, String message) 
            throws IOException {
        
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        CommonResponse<Void> errorResponse = CommonResponse.error(errorCode, message);
        
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}
