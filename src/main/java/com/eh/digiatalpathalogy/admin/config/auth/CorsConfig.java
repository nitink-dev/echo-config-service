package com.eh.digiatalpathalogy.admin.config.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    @RefreshScope
    public CorsConfigurationSource corsConfigurationSource(SecurityCorsConfig securityCorsConfig) {
        log.info("Initializing CORS configuration (RefreshScope active)");

        boolean allowCredentials = securityCorsConfig.isAllowCredentials();
        long maxAge = securityCorsConfig.getMaxAge();

        final List<String> originPatterns = splitAndTrim(securityCorsConfig.getAllowedOrigins());
        final List<String> methods = splitAndTrim(securityCorsConfig.getAllowedMethods());
        final List<String> headers = splitAndTrim(securityCorsConfig.getAllowedHeaders());

        log.debug("Resolved CORS properties: allowed-origins='{}', allowed-methods='{}', allowed-headers='{}', allow-credentials={}, max-age={}",
                originPatterns, methods, headers, allowCredentials, maxAge
        );

        if (originPatterns.isEmpty()) {
            log.warn("No allowed origins configured (security.cors.allowed-origins). All cross-origin requests will be rejected.");
        } else if (originPatterns.size() == 1 && "*".equals(originPatterns.get(0))) {
            log.info("CORS is permissive: allowing ALL origins via origin patterns ('*').");
            if (allowCredentials) {
                log.info("Credentials are allowed; Spring will echo the request Origin for matches (not '*') to satisfy browser rules.");
            }
        }

        if (methods.stream().noneMatch("OPTIONS"::equalsIgnoreCase)) {
            log.warn("OPTIONS is missing from allowed methods. Browser preflight requests may fail.");
        }

        if (headers.isEmpty()) {
            log.warn("No allowed headers configured (security.cors.allowed-headers). Requests with custom headers may be blocked.");
        } else if (headers.size() == 1 && "*".equals(headers.get(0))) {
            log.info("Allowing ALL request headers ('*').");
        }

        var config = new CorsConfiguration();

        if (originPatterns.size() == 1 && "*".equals(originPatterns.get(0))) {
            config.addAllowedOriginPattern("*");
        } else {
            config.setAllowedOriginPatterns(originPatterns);
        }

        config.setAllowedMethods(methods);
        config.setAllowedHeaders(headers);
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("CORS configuration registered for '/**' | origins: {} | methods: {} | headers: {} | credentials: {} | maxAge(s): {}", originPatterns.isEmpty() ? "[]" : originPatterns, methods.isEmpty() ? "[]" : methods,
                headers.isEmpty() ? "[]" : headers, allowCredentials, maxAge
        );

        return source;
    }

    private List<String> splitAndTrim(String input) {
        if (!StringUtils.hasText(input)) return List.of();
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

}