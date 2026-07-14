package com.eh.digiatalpathalogy.admin.config.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.ldap")
public record LdapAuthConfig(String userDnPattern, UserSearch userSearch, Groups groups, Timeouts timeouts,
                             JndiPool jndiPool) {

    public record UserSearch(String base, String filter, boolean searchSubtree) {
        public UserSearch {
            if (base == null) base = "";
            if (filter == null || filter.isBlank()) filter = "";
        }
    }

    public record Groups(String base, String filter, String roleAttribute, boolean searchSubtree) {
        public Groups {
            if (base == null || base.isBlank()) base = "";
            if (filter == null || filter.isBlank()) filter = "";
            if (roleAttribute == null || roleAttribute.isBlank()) roleAttribute = "cn";
        }
    }

    public record Timeouts(Duration connect, Duration read) {
        public Timeouts {
            if (connect == null) connect = Duration.ofSeconds(5);
            if (read == null) read = Duration.ofSeconds(5);
        }
    }

    public record JndiPool(boolean enabled) {
    }
}