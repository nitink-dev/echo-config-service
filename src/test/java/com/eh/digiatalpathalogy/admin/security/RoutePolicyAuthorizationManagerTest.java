package com.eh.digiatalpathalogy.admin.security;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.testdata.LoginTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutePolicyAuthorizationManagerTest {

    @Spy
    private SecurityPolicyConfig policies = LoginTestData.securityPolicyConfig();

    @InjectMocks
    private RoutePolicyAuthorizationManager manager;

    @Test
    @DisplayName("permitAll → should ALLOW access")
    void permitAllRuleShouldAllowAccess() {
        ServerWebExchange exchange = createExchange("/api/public/login", HttpMethod.GET);
        AuthorizationContext ctx = new AuthorizationContext(exchange);

        StepVerifier.create(manager.check(Mono.empty(), ctx))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    @Test
    @DisplayName("Unmatched path → should DENY access")
    void noRuleShouldDenyAccess() {
        ServerWebExchange exchange = createExchange("/unknown/path", HttpMethod.GET);
        AuthorizationContext ctx = new AuthorizationContext(exchange);

        StepVerifier.create(manager.check(Mono.empty(), ctx))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }

    @Test
    @DisplayName("User with required scope → should ALLOW access")
    void requiredScopeShouldAllowAccess() {
        Authentication auth = mockAuth(Set.of("SCOPE_platform.read"));

        ServerWebExchange exchange = createExchange("/api/secure/data", HttpMethod.GET);
        AuthorizationContext ctx = new AuthorizationContext(exchange);

        StepVerifier.create(manager.check(Mono.just(auth), ctx))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    @Test
    @DisplayName("User missing required scope → should DENY access")
    void missingScopeShouldDenyAccess() {
        Authentication auth = mockAuth(Set.of("SCOPE_other.scope"));

        ServerWebExchange exchange = createExchange("/api/secure/data", HttpMethod.GET);
        AuthorizationContext ctx = new AuthorizationContext(exchange);

        StepVerifier.create(manager.check(Mono.just(auth), ctx))
                .expectNextMatches(decision -> !decision.isGranted())
                .verifyComplete();
    }

    @Test
    @DisplayName("User with SUPER scope → should ALWAYS allow access")
    void superScopeShouldAllowAccess() {
        Authentication auth = mockAuth(Set.of("SCOPE_platform.admin"));

        ServerWebExchange exchange = createExchange("/api/secure/data", HttpMethod.GET);
        AuthorizationContext ctx = new AuthorizationContext(exchange);

        StepVerifier.create(manager.check(Mono.just(auth), ctx))
                .expectNextMatches(AuthorizationDecision::isGranted)
                .verifyComplete();
    }

    private Authentication mockAuth(Set<String> authorities) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        Collection<SimpleGrantedAuthority> granted = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        when(auth.getAuthorities()).thenReturn((Collection) granted);
        return auth;
    }

    private ServerWebExchange createExchange(String path, HttpMethod method) {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, URI.create(path))
                .build();

        return MockServerWebExchange.from(request);
    }
}