package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.model.auth.LoginRequest;
import com.eh.digiatalpathalogy.admin.testdata.LoginTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    ReactiveAuthenticationManager authenticationManager;
    @Mock
    ServerSecurityContextRepository securityContextRepository;
    @Mock
    ServerCsrfTokenRepository csrfTokenRepository;
    @InjectMocks
    AuthService service;
    @Spy
    SecurityPolicyConfig securityPolicyConfig = LoginTestData.securityPolicyConfig();

    @Test
    void login_success_mapsGroupsToRolesAndScopes_savesContextAndCsrf() {
        LoginRequest req = LoginTestData.loginRequest();

        // simulate LDAP authority "eh-admin" returned
        Authentication ldapAuth = new UsernamePasswordAuthenticationToken(
                req.username(), req.password(), List.of(new SimpleGrantedAuthority("eh-admin")));

        when(authenticationManager.authenticate(any())).thenReturn(Mono.just(ldapAuth));

        when(securityContextRepository.save(any(), any(SecurityContextImpl.class)))
                .thenReturn(Mono.empty());

        when(csrfTokenRepository.saveToken(any(), any())).thenReturn(Mono.empty());
        when(csrfTokenRepository.generateToken(any()))
                .thenReturn(Mono.just(new DefaultCsrfToken("X-CSRF-TOKEN", "X-CSRF-TOKEN", "abc123")));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build());

        StepVerifier.create(service.login(req, exchange))
                .expectNextMatches(resp ->
                        req.username().equals(resp.username())
                                && resp.roles().contains("ROLE_ADMIN")
                                && resp.scopes().contains("platform.read")
                                && resp.scopes().contains("platform.update")
                )
                .verifyComplete();

        verify(securityContextRepository).save(eq(exchange), any(SecurityContextImpl.class));
        verify(csrfTokenRepository, times(2)).saveToken(eq(exchange), any());
        verify(csrfTokenRepository).generateToken(eq(exchange));
    }

    @Test
    void login_invalidCredentials_propagatesBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(Mono.error(new BadCredentialsException("Invalid credentials")));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build());

        StepVerifier.create(service.login(new LoginRequest("bad", "bad"), exchange))
                .expectError(BadCredentialsException.class)
                .verify();
    }

    @Test
    void logout_invalidatesSession_clearsContextAndCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/logout").build());

        when(securityContextRepository.save(eq(exchange), isNull())).thenReturn(Mono.empty());
        when(csrfTokenRepository.saveToken(eq(exchange), isNull())).thenReturn(Mono.empty());

        StepVerifier.create(service.logout(exchange))
                .verifyComplete();

        verify(securityContextRepository).save(eq(exchange), isNull());
        verify(csrfTokenRepository).saveToken(eq(exchange), isNull());
    }
}