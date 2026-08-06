package com.eh.digiatalpathalogy.admin.support;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;

/**
 * Minimal mock-based config used ONLY for unit tests.
 * Ensures security components can be created without full Spring context.
 */
@TestConfiguration
public class SecurityConfigTest {


    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    @Bean
    public SecurityPolicyConfig securityPolicyConfig() {
        return Mockito.mock(SecurityPolicyConfig.class);
    }

    // IMPORTANT — different bean name
    @Bean
    public WebFilter testSessionRotationBypassFilter() {
        return (exchange, chain) -> chain.filter(exchange);
    }

}