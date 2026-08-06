package com.eh.digiatalpathalogy.admin.security;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.exception.AccessDeniedHandler;
import com.eh.digiatalpathalogy.admin.exception.AuthenticationEntryPoint;
import com.eh.digiatalpathalogy.admin.util.JsonResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.*;
import org.springframework.security.web.server.util.matcher.*;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Configuration
@EnableReactiveMethodSecurity
public class WebFluxHttpSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WebFluxHttpSecurityConfig.class);

    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        log.info("Using WebSessionServerSecurityContextRepository");
        return new WebSessionServerSecurityContextRepository();
    }

    @Bean
    @RefreshScope
    public RoutePolicyAuthorizationManager routePolicyAuthorizationManager(SecurityPolicyConfig securityPolicyConfig) {
        log.info("Loading Route Policy Authorization Manager (Refreshable)");
        return new RoutePolicyAuthorizationManager(securityPolicyConfig);
    }

    @Bean
    public WebFilter csrfCookieWebFilter() {
        log.info("CSRF cookie WebFilter initialized");
        return (exchange, chain) ->
                exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty())
                        .then(chain.filter(exchange));
    }

    @Bean
    public ServerCsrfTokenRepository serverCsrfTokenRepository() {
        log.info("Configuring CSRF Cookie Repository: cookieName=XSRF-TOKEN, header=X-XSRF-TOKEN");
        var csrfRepo = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieName("XSRF-TOKEN");
        csrfRepo.setHeaderName("X-XSRF-TOKEN");
        csrfRepo.setCookieCustomizer(b -> b.sameSite("Lax").secure(false));
        return csrfRepo;
    }

    @Bean
    public ServerAccessDeniedHandler serverAccessDeniedHandler(JsonResponseWriter jsonResponseWriter) {
        return new AccessDeniedHandler(jsonResponseWriter);
    }

    @Bean
    public ServerAuthenticationEntryPoint serverAuthenticationEntryPoint(JsonResponseWriter jsonResponseWriter) {
        return new AuthenticationEntryPoint(jsonResponseWriter);
    }

    @Bean
    @RefreshScope
    public SecurityWebFilterChain springSecurity(ServerHttpSecurity http, ServerCsrfTokenRepository serverCsrfTokenRepository,
                                                 ServerSecurityContextRepository securityContextRepository,
                                                 RoutePolicyAuthorizationManager routePolicyAuthorizationManager,
                                                 SecurityPolicyConfig securityPolicyConfig,
                                                 ServerAccessDeniedHandler serverAccessDeniedHandler,
                                                 ServerAuthenticationEntryPoint serverAuthenticationEntryPoint) {

        log.info("Building Spring Security WebFlux Filter Chain...");
        List<String> publicPaths = getPublicPaths(securityPolicyConfig.getRoutes().rules());
        log.info("Public paths (permitAll): {}", publicPaths);
        http
                .cors(corsSpec -> {})
                .securityContextRepository(securityContextRepository)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(serverAuthenticationEntryPoint)
                        .accessDeniedHandler(serverAccessDeniedHandler))
                .authorizeExchange(ex -> {
                    ex.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    if (publicPaths != null && !publicPaths.isEmpty()) {
                        ex.pathMatchers(publicPaths.toArray(new String[0])).permitAll();
                    }
                    log.info("All other paths secured using dynamic RoutePolicyAuthorizationManager");
                    ex.anyExchange().access(routePolicyAuthorizationManager);
                })
                .csrf(csrf -> {
                    log.info("Enabling CSRF protection with cookie-based token");
                    csrf.csrfTokenRepository(serverCsrfTokenRepository);
                    csrf.csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler());
                    csrf.requireCsrfProtectionMatcher(setUpCsrfProtectionMatcher(publicPaths));
                    csrf.accessDeniedHandler(serverAccessDeniedHandler);
                })
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        log.info("Spring Security WebFlux Chain successfully initialized");
        return http.build();
    }

    private ServerWebExchangeMatcher setUpCsrfProtectionMatcher(List<String> publicPaths) {
        log.info("Setting up CSRF matchers…");
        List<ServerWebExchangeMatcher> ignores = new ArrayList<>();
        ignores.add(new PathPatternParserServerWebExchangeMatcher("/**", HttpMethod.OPTIONS));

        final Set<HttpMethod> csrfUnsafeMethod = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

        for (String path : publicPaths) {
            if (!StringUtils.hasText(path)) continue;
            for (HttpMethod method : csrfUnsafeMethod) {
                ignores.add(new PathPatternParserServerWebExchangeMatcher(path.trim(), method));
            }
        }
        ServerWebExchangeMatcher require = CsrfWebFilter.DEFAULT_CSRF_MATCHER;
        if (!ignores.isEmpty()) {
            var ignoredOr = new OrServerWebExchangeMatcher(ignores);
            require = new AndServerWebExchangeMatcher(CsrfWebFilter.DEFAULT_CSRF_MATCHER, new NegatedServerWebExchangeMatcher(ignoredOr));
        }
        return require;
    }

    private List<String> getPublicPaths(List<SecurityPolicyConfig.RouteRule> routes) {
        return routes.stream().filter(SecurityPolicyConfig.RouteRule::permitAll).map(SecurityPolicyConfig.RouteRule::pattern).toList();
    }

}
