package com.eh.digiatalpathalogy.admin.security;

import com.eh.digiatalpathalogy.admin.config.auth.LdapAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LdapAuthConfigTests {

    @Test
    @DisplayName("UserSearch defaults to base=ou=people and filter=(uid={0}) when null/blank")
    void userSearch_defaults() {
        LdapAuthConfig.UserSearch us = new LdapAuthConfig.UserSearch("ou=people", "(uid={0})",true);

        assertEquals("ou=people", us.base());
        assertEquals("(uid={0})", us.filter());
    }

    @Test
    @DisplayName("Groups default fallback values applied correctly")
    void groups_defaults() {
        LdapAuthConfig.Groups g = new LdapAuthConfig.Groups("ou=groups", "(|(member={0})(uniqueMember={0})(memberUid={1}))", "cn", true);

        assertEquals("ou=groups", g.base());
        assertEquals("(|(member={0})(uniqueMember={0})(memberUid={1}))", g.filter());
        assertEquals("cn", g.roleAttribute());
        assertTrue(g.searchSubtree());
    }

    @Test
    @DisplayName("Timeouts default to connect=5s, read=5s when null")
    void timeouts_defaults() {
        LdapAuthConfig.Timeouts t = new LdapAuthConfig.Timeouts(null, null);

        assertEquals(Duration.ofSeconds(5), t.connect());
        assertEquals(Duration.ofSeconds(5), t.read());
    }

    @Test
    @DisplayName("JndiPool stores flag correctly")
    void jndiPool_flag() {
        LdapAuthConfig.JndiPool pool = new LdapAuthConfig.JndiPool(true);
        assertTrue(pool.enabled());
    }

    @Test
    @DisplayName("Full LdapAuthConfig stores nested config objects")
    void full_config() {
        var cfg = new LdapAuthConfig(
                "uid={0},ou=people,dc=example,dc=com",
                new LdapAuthConfig.UserSearch("ou=u", "(uid={0})",true),
                new LdapAuthConfig.Groups("ou=g", "(member={0})", "cn", true),
                new LdapAuthConfig.Timeouts(Duration.ofSeconds(10), Duration.ofSeconds(20)),
                new LdapAuthConfig.JndiPool(false)
        );

        assertEquals("uid={0},ou=people,dc=example,dc=com", cfg.userDnPattern());
        assertEquals("ou=u", cfg.userSearch().base());
        assertEquals("ou=g", cfg.groups().base());
        assertEquals(false, cfg.jndiPool().enabled());
        assertEquals(Duration.ofSeconds(10), cfg.timeouts().connect());
    }
}
