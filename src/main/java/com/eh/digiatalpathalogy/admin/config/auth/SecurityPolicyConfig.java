package com.eh.digiatalpathalogy.admin.config.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.NonNull;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Binds security policies and compiles method-aware route rules.
 */
@ConfigurationProperties(prefix = "security.policies")
@RefreshScope
public class SecurityPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyConfig.class);

    private Map<String, String> ldapToAppRole;
    private Routes routes;
    private Map<String, List<String>> roleToScopes;
    private SessionPolicy session;
    private List<String> superScopes;
    private volatile List<CompiledRule> compiled;

    public SecurityPolicyConfig(Map<String, String> ldapToAppRole, Map<String, List<String>> roleToScopes, SessionPolicy session, List<String> superScopes, Routes routes) {
        log.info("Initializing SecurityPolicyConfig (new instance created/refreshed)");
        this.compiled = null;
        this.ldapToAppRole = (ldapToAppRole != null) ? ldapToAppRole : new HashMap<>();
        this.roleToScopes = (roleToScopes != null) ? roleToScopes : new HashMap<>();
        this.session = (session != null) ? session : new SessionPolicy(null);
        this.superScopes = (superScopes != null) ? List.copyOf(superScopes) : List.of();
        this.routes = (routes != null) ? routes : new Routes(null);
        log.info("SecurityPolicyConfig loaded: routes={}, superScopes={}, ldapRoles={}, roleScopes={}", this.routes.rules().size(), this.superScopes.size(), this.ldapToAppRole.size(), this.roleToScopes.size());
    }


    public record SessionPolicy(java.time.Duration rotateAfter) {
        public SessionPolicy {
            rotateAfter = (rotateAfter != null) ? rotateAfter : java.time.Duration.ofMinutes(20);
        }
    }

    public record Routes(List<RouteRule> rules) {
        public Routes {
            rules = (rules != null) ? List.copyOf(rules) : List.of();
        }
    }

    public record RouteRule(String pattern, List<HttpMethod> methods, boolean permitAll, List<String> requiredScopes) {
        public RouteRule {
            pattern = (pattern != null) ? pattern.trim() : null;
            methods = (methods != null) ? List.copyOf(methods) : List.of();
            requiredScopes = (requiredScopes != null) ? List.copyOf(requiredScopes) : List.of();
        }
    }

    public record Rule(boolean permitAll, Set<String> requiredScopes) {
        public Rule {
            requiredScopes = (requiredScopes != null)
                    ? Set.copyOf(requiredScopes.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                    : Set.of();
        }

        public boolean isPermitAll() {
            return permitAll;
        }
    }

    private List<CompiledRule> compiled() {
        var local = compiled;
        if (local != null) return local;

        synchronized (this) {
            if (compiled != null) return compiled;
            log.info("Compiling security route policies (cache miss)");
            PathPatternParser parser = PathPatternParser.defaultInstance;
            List<CompiledRule> list = new ArrayList<>();

            if (routes != null && routes.rules() != null) {
                for (RouteRule r : routes.rules()) {
                    if (r.pattern() == null || r.pattern().isBlank()) continue;

                    PathPattern p = parser.parse(r.pattern());
                    Set<HttpMethod> methods = (r.methods() == null || r.methods().isEmpty()) ? new HashSet<>(Arrays.asList(HttpMethod.values()))
                            : new HashSet<>(r.methods);

                    list.add(new CompiledRule(p, methods, r.permitAll(), r.requiredScopes()));
                    log.debug("Compiled rule: pattern={}, methods={}, permitAll={}, scopes={}", r.pattern(), methods, r.permitAll(), r.requiredScopes());
                }
            }
            compiled = List.copyOf(list);
            log.info("Security route policy compilation completed. Total compiled rules={}", compiled.size());
            return compiled;
        }
    }

    private static final class CompiledRule {
        final PathPattern pattern;
        final Set<HttpMethod> methods;
        final boolean permitAll;
        final List<String> requiredScopes;

        CompiledRule(PathPattern pattern, Set<HttpMethod> methods, boolean permitAll, List<String> scopes) {
            this.pattern = pattern;
            this.methods = methods;
            this.permitAll = permitAll;
            this.requiredScopes = (scopes != null) ? List.copyOf(scopes) : List.of();
        }
    }

    public Optional<Rule> match(@NonNull String path, @NonNull HttpMethod method) {
        PathContainer container = PathContainer.parsePath(path);
        for (var cr : compiled()) {
            if (cr.pattern.matches(container) && cr.methods.contains(method)) {
                return Optional.of(new Rule(cr.permitAll, new HashSet<>(cr.requiredScopes)));
            }
        }
        return Optional.empty();
    }

    public Map<String, String> getLdapToAppRole() {
        return ldapToAppRole;
    }

    public Routes getRoutes() {
        return routes;
    }

    public Map<String, List<String>> getRoleToScopes() {
        return roleToScopes;
    }

    public SessionPolicy getSession() {
        return session;
    }

    public List<String> getSuperScopes() {
        return superScopes;
    }

    public void setLdapToAppRole(Map<String, String> ldapToAppRole) {
        this.ldapToAppRole = ldapToAppRole;
    }

    public void setRoutes(Routes routes) {
        this.routes = routes;
    }

    public void setRoleToScopes(Map<String, List<String>> roleToScopes) {
        this.roleToScopes = roleToScopes;
    }

    public void setSession(SessionPolicy session) {
        this.session = session;
    }

    public void setSuperScopes(List<String> superScopes) {
        this.superScopes = superScopes;
    }
}