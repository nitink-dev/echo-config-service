package com.eh.digiatalpathalogy.admin.security;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Method-aware, scope-based authorization manager for WebFlux.
 * Expects scopes to be present as authorities with "SCOPE_" prefix.
 */
public class RoutePolicyAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final SecurityPolicyConfig policies;

    public RoutePolicyAuthorizationManager(SecurityPolicyConfig policies) {
        this.policies = policies;
    }

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {

        ServerWebExchange exchange = context.getExchange();
        ServerHttpRequest request = exchange.getRequest();

        String path = request.getPath().value();
        HttpMethod method = request.getMethod() != null ? request.getMethod() : HttpMethod.GET;

        var ruleOpt = policies.match(path, method);
        if (ruleOpt.isEmpty()) {
            return Mono.just(new AuthorizationDecision(false));
        }

        var rule = ruleOpt.get();
        if (rule.isPermitAll()) {
            return Mono.just(new AuthorizationDecision(true));
        }

        return authentication
                .map(auth -> {
                    if (auth == null || !auth.isAuthenticated()) {
                        return new AuthorizationDecision(false);
                    }
                    Set<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

                    boolean superScope = policies.getSuperScopes().stream().anyMatch(s -> authorities.contains("SCOPE_" + s));
                    if (superScope) return new AuthorizationDecision(true);

                    boolean requiredScope = rule.requiredScopes().stream().anyMatch(req -> authorities.contains("SCOPE_" + req));
                    return new AuthorizationDecision(requiredScope);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}