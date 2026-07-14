package com.eh.digiatalpathalogy.admin.security;


import com.eh.digiatalpathalogy.admin.config.auth.LdapAuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ldap.LdapProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerAdapter;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
public class ReactiveLdapAuthenticationConfig {

    private static final Logger log = LoggerFactory.getLogger(ReactiveLdapAuthenticationConfig.class);

    @Bean
    public DefaultSpringSecurityContextSource contextSource(LdapProperties bootLdapProps, LdapAuthConfig ldapAuthConfig) {

        var ldapUrls = Arrays.asList(Optional.ofNullable(bootLdapProps.getUrls()).orElse(new String[0]));

        log.info("Initializing LDAP Context Source...");
        log.info("LDAP URLs = {}", ldapUrls);
        log.info("LDAP Base DN = {}", bootLdapProps.getBase());

        var contextSource = new DefaultSpringSecurityContextSource(ldapUrls, bootLdapProps.getBase());
        if (bootLdapProps.getUsername() != null) {
            String maskedDn = bootLdapProps.getUsername().substring(0, Math.min(10, bootLdapProps.getUsername().length()));
            log.info("LDAP Bind DN (masked) = {}***", maskedDn);
            contextSource.setUserDn(bootLdapProps.getUsername());
        }
        if (bootLdapProps.getPassword() != null) {
            log.info("LDAP Bind Password = **** (hidden)");
            contextSource.setPassword(bootLdapProps.getPassword());
        }


        Map<String, Object> baseEnv = new HashMap<>(bootLdapProps.getBaseEnvironment());
        baseEnv.putIfAbsent("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapAuthConfig.timeouts().connect().toMillis()));
        baseEnv.putIfAbsent("com.sun.jndi.ldap.read.timeout", String.valueOf(ldapAuthConfig.timeouts().read().toMillis()));
        if (ldapAuthConfig.jndiPool() != null && ldapAuthConfig.jndiPool().enabled()) {
            baseEnv.put("com.sun.jndi.ldap.connect.pool", "true");
        }
        baseEnv.put("java.naming.referral", "follow");

        contextSource.setBaseEnvironmentProperties(baseEnv);
        contextSource.afterPropertiesSet();

        log.info("LDAP Context Source successfully initialized.");
        return contextSource;
    }

    @Bean
    public AuthenticationManager blockingLdapAuthManager(DefaultSpringSecurityContextSource contextSource, LdapAuthConfig ldapAuthConfig) {
        var bindAuthenticator = new BindAuthenticator(contextSource);
        if (ldapAuthConfig.userDnPattern() != null && !ldapAuthConfig.userDnPattern().isBlank()) {
            log.info("Using userDnPattern for LDAP login: {}", ldapAuthConfig.userDnPattern());
            bindAuthenticator.setUserDnPatterns(new String[]{ldapAuthConfig.userDnPattern()});
        } else {
            log.info("Using user search filter for LDAP login...");
            log.info("Search Base = {}", ldapAuthConfig.userSearch().base());
            log.info("Search Filter = {}", ldapAuthConfig.userSearch().filter());
            var userSearch = new FilterBasedLdapUserSearch(ldapAuthConfig.userSearch().base(), ldapAuthConfig.userSearch().filter(), contextSource);
            userSearch.setSearchSubtree(ldapAuthConfig.userSearch().searchSubtree());
            bindAuthenticator.setUserSearch(userSearch);
        }
        var provider = getLdapAuthenticationProvider(contextSource, ldapAuthConfig, bindAuthenticator);
        log.info("LDAP Authentication Provider initialized.");
        return new ProviderManager(provider);
    }

    private LdapAuthenticationProvider getLdapAuthenticationProvider(DefaultSpringSecurityContextSource contextSource, LdapAuthConfig ldapAuthConfig, BindAuthenticator bindAuthenticator) {

        var ldapConfigGroups = ldapAuthConfig.groups();

        log.info("Configuring LDAP Group Lookup...");
        log.info("Group Search Base = {}", ldapConfigGroups.base());
        log.info("Group Filter = {}", ldapConfigGroups.filter());
        log.info("Group Role Attribute = {}", ldapConfigGroups.roleAttribute());


        var groups = new DefaultLdapAuthoritiesPopulator(contextSource, ldapConfigGroups.base());
        groups.setGroupSearchFilter(ldapConfigGroups.filter());
        groups.setGroupRoleAttribute(ldapConfigGroups.roleAttribute());
        groups.setSearchSubtree(ldapConfigGroups.searchSubtree());

        var mapper = new SimpleAuthorityMapper();
        mapper.setConvertToUpperCase(true);
        mapper.setPrefix("ROLE_");

        var provider = new LdapAuthenticationProvider(bindAuthenticator, groups);
        provider.setAuthoritiesMapper(mapper);
        provider.setUserDetailsContextMapper(new InetOrgPersonContextMapper());

        return provider;
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(AuthenticationManager blockingLdapAuthManager) {
        log.info("Initializing ReactiveAuthenticationManager (adapting blocking provider).");
        return new ReactiveAuthenticationManagerAdapter(blockingLdapAuthManager);
    }
}