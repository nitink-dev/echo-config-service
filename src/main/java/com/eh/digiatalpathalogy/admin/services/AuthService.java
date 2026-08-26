package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.model.RouteRuleDTO;
import com.eh.digiatalpathalogy.admin.model.auth.LoginRequest;
import com.eh.digiatalpathalogy.admin.model.auth.LoginResponse;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.ldap.userdetails.InetOrgPerson;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.AUTH_CONFIG;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String ROLE = "ROLE_";

    private final ReactiveAuthenticationManager authManager;
    private final SecurityPolicyConfig policies;
    private final ServerSecurityContextRepository securityContextRepository;
    private final ServerCsrfTokenRepository serverCsrfTokenRepository;
    private final SecurityPolicyConfig securityPolicyConfig;
    private final RedisEntityStore redisStore;
    private final Duration sessionTimeout;

    public AuthService(ReactiveAuthenticationManager authManager, SecurityPolicyConfig policies, ServerSecurityContextRepository securityContextRepository,
                       ServerCsrfTokenRepository serverCsrfTokenRepository, SecurityPolicyConfig securityPolicyConfig, RedisEntityStore redisStore, @Value("${spring.session.timeout:30m}") Duration sessionTimeout) {
        this.authManager = authManager;
        this.policies = policies;
        this.securityContextRepository = securityContextRepository;
        this.serverCsrfTokenRepository = serverCsrfTokenRepository;
        this.securityPolicyConfig = securityPolicyConfig;
        this.redisStore = redisStore;
        this.sessionTimeout = sessionTimeout;
    }

    public Mono<LoginResponse> login(LoginRequest req, ServerWebExchange exchange) {

        log.info("Login attempt started for username='{}'", req.username());

        Authentication token = new UsernamePasswordAuthenticationToken(req.username(), req.password());

        return authManager.authenticate(token)
                .doOnSubscribe(sub -> log.debug("Authenticating user='{}' via LDAP", req.username()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Authentication FAILED for username='{}' – invalid credentials", req.username());
                    return Mono.error(new BadCredentialsException("Invalid credentials"));
                }))
                .doOnError(e -> log.error("LDAP authentication FAILED for username='{}' - {}", req.username(), e.getMessage()))
                .flatMap(auth -> {
                    log.info("LDAP authentication SUCCESS for user='{}'", req.username());
                    Set<String> appRoles = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .map(String::toUpperCase)
                            .map(role -> role.startsWith(ROLE) ? role : ROLE + role)
                            .map(role -> {
                                String simple = role.replace(ROLE, "").toLowerCase();
                                String mapped = policies.getLdapToAppRole().getOrDefault(simple, role);
                                if (!mapped.equals(role)) {
                                    log.debug("Mapped LDAP role '{}' → Application role '{}'", role, mapped);
                                }
                                return mapped;
                            })
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    Set<String> scopes = appRoles.stream()
                            .flatMap(r -> policies.getRoleToScopes().getOrDefault(r, List.of()).stream())
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    log.info("Resolved scopes for user '{}': {}", req.username(), scopes);

                    List<GrantedAuthority> finalAuths = new ArrayList<>();
                    appRoles.forEach(r -> finalAuths.add(new SimpleGrantedAuthority(r)));
                    scopes.forEach(s -> finalAuths.add(new SimpleGrantedAuthority("SCOPE_" + s)));

                    log.debug("Saving security context for user='{}'", req.username());
                    String displayName = displayName(auth, req.username());
                    Authentication enriched = new UsernamePasswordAuthenticationToken(new LoginResponse(req.username(), displayName, null, null, null), null, finalAuths);
                    LoginResponse loginResponse = new LoginResponse(req.username(), displayName, new ArrayList<>(appRoles), new ArrayList<>(scopes), sessionTimeout == null ? 30 : sessionTimeout.toMinutes());
                    return saveSessionAndGenerateCsrfToken(exchange, enriched, req, loginResponse);
                });
    }

    private Mono<LoginResponse> saveSessionAndGenerateCsrfToken(ServerWebExchange exchange, Authentication enriched, LoginRequest req, LoginResponse loginResponse) {
        return securityContextRepository.save(exchange, new SecurityContextImpl(enriched))
                .doOnSuccess(v -> log.trace("SecurityContext saved for user='{}'", req.username()))
                .then(serverCsrfTokenRepository.saveToken(exchange, null))
                .doOnSuccess(v -> log.trace("Old CSRF token cleared for user='{}'", req.username()))
                .then(serverCsrfTokenRepository.generateToken(exchange)
                        .flatMap(csrfToken -> {
                            log.trace("Generated new CSRF token for user='{}'", req.username());
                            return serverCsrfTokenRepository.saveToken(exchange, csrfToken);
                        }))
                .doOnSuccess(v -> log.info("Login flow completed successfully for user='{}'", req.username()))
                .thenReturn(loginResponse);
    }

    private String displayName(Authentication auth, String username) {
        if (auth.getPrincipal() instanceof InetOrgPerson inetOrgPerson) {
            String dn = inetOrgPerson.getDisplayName();
            if (dn != null && !dn.isBlank()) return dn;
        }
        return username;
    }


    public Mono<Void> logout(ServerWebExchange exchange) {

        log.info("Logout request received");
        return exchange.getSession()
                .doOnNext(session -> {
                    log.debug("Invalidating session id={}", session.getId());
                    session.invalidate();
                })
                .then(securityContextRepository.save(exchange, null))
                .doOnSuccess(v -> log.trace("SecurityContext cleared on logout"))
                .then(serverCsrfTokenRepository.saveToken(exchange, null))
                .doOnSuccess(v -> log.trace("CSRF token cleared on logout"))
                .then(Mono.fromRunnable(() -> log.info("Logout completed successfully")));
    }

    public Flux<RouteRuleDTO> getAuthConfig() {
        return redisStore.findByKeyWithFallback(AUTH_CONFIG, () -> Mono.fromSupplier(this::loadAuthConfig), RouteRuleDTO[].class)
                .flatMapMany(arr -> (arr == null || arr.length == 0) ? Flux.empty() : Flux.fromArray(arr));
    }

    /**
     * Build the fallback payload from your YAML-backed SecurityPolicyConfig.
     */
    private RouteRuleDTO[] loadAuthConfig() {
        List<SecurityPolicyConfig.RouteRule> rules = Optional.ofNullable(securityPolicyConfig)
                .map(SecurityPolicyConfig::getRoutes)
                .map(SecurityPolicyConfig.Routes::rules)
                .orElseGet(Collections::emptyList);
        return rules.stream()
                .map(this::fromDomain)
                .toArray(RouteRuleDTO[]::new);
    }

    private RouteRuleDTO fromDomain(SecurityPolicyConfig.RouteRule domain) {
        var methodNames = domain.methods().stream()
                .map(Object::toString)
                .map(String::toUpperCase).toList();

        return new RouteRuleDTO(domain.pattern(), methodNames, domain.permitAll(), domain.requiredScopes());
    }

}