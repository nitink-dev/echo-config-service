package com.eh.digiatalpathalogy.admin.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import reactor.test.StepVerifier;

class CsrfTokenRepositoryTests {

    private CookieServerCsrfTokenRepository repo() {
        CookieServerCsrfTokenRepository repo = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieName("XSRF-TOKEN");
        repo.setHeaderName("X-XSRF-TOKEN");
        repo.setCookieCustomizer(c -> c.sameSite("None").secure(true));
        return repo;
    }

    @Test
    @DisplayName("generateToken() produces valid token with correct header/cookie names")
    void generateToken_ok() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        StepVerifier.create(repo().generateToken(exchange))
                .assertNext(token -> {
                    assert token instanceof DefaultCsrfToken;
                    assert token.getHeaderName().equals("X-XSRF-TOKEN");
                    assert token.getParameterName().equals("_csrf");
                    assert token.getToken() != null;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("saveToken(null) removes cookie")
    void saveToken_null_removesCookie() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        StepVerifier.create(repo().saveToken(exchange, null))
                .verifyComplete();

        var cookies = exchange.getResponse().getCookies().get("XSRF-TOKEN");
        assert cookies != null;
        assert cookies.get(0).getValue().isEmpty();
    }

    @Test
    @DisplayName("saveToken() creates secure SameSite=None CSRF cookie")
    void saveToken_setsCookie() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "abc123");

        StepVerifier.create(repo().saveToken(exchange, token))
                .verifyComplete();

        var cookie = exchange.getResponse().getCookies().get("XSRF-TOKEN").get(0);
        assert cookie.getValue().equals("abc123");
        assert cookie.isSecure();
        assert "None".equalsIgnoreCase(cookie.getSameSite());
    }
}