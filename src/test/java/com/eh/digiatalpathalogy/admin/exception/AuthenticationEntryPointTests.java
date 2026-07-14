package com.eh.digiatalpathalogy.admin.exception;

import com.eh.digiatalpathalogy.admin.util.JsonResponseWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import reactor.test.StepVerifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class AuthenticationEntryPointTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthenticationEntryPoint handler = new AuthenticationEntryPoint(new JsonResponseWriter(mapper));

    static class DummyAuthEx extends AuthenticationException {
        DummyAuthEx(String msg) {
            super(msg);
        }
    }

    @Test
    @DisplayName("Unauthorized → returns 401 with default JSON body")
    void unauthorized_returns401_json() {

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/secure/test").build());
        StepVerifier.create(handler.commence(exchange, new DummyAuthEx("No auth"))).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
                .contains("\"error\"")
                .contains("\"errorDescription\"");
    }

    @Test
    @DisplayName("Unauthorized → default error message when no session")
    void unauthorized_withoutSession() throws Exception {

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login").build());
        StepVerifier.create(handler.commence(exchange, new DummyAuthEx("fail"))).verifyComplete();
        var tree = mapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tree.get("error").asText()).isNotNull();
        assertThat(tree.get("errorDescription").asText()).isNotNull();

    }

    @Test
    @DisplayName("Session expired → returns Session Expired error")
    void sessionExpired_whenSessionExists_butSecurityContextMissing() throws Exception {

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/data").build());
        exchange.getSession().block();
        StepVerifier.create(handler.commence(exchange, new DummyAuthEx("expired"))).verifyComplete();

        var json = exchange.getResponse().getBodyAsString().block();
        var tree = mapper.readTree(json);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tree.get("error").asText()).isEqualTo("Session Expired");
        assertThat(tree.get("errorDescription").asText()).contains("expired");
    }

    @Test
    @DisplayName("Valid session with security context → treated as unauthorized, not expired")
    void validSession_withSecurityContext_notExpired() throws Exception {

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/secure").build());

        exchange.getSession().doOnNext(session -> session.getAttributes().put(
                        WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME, new Object())).block();
        StepVerifier.create(handler.commence(exchange, new DummyAuthEx("fail"))).verifyComplete();

        var tree = mapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tree.get("error").asText()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("Null exception message handled safely")
    void unauthorized_nullMessage_safeHandling() throws Exception {

        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login").build());
        StepVerifier.create(handler.commence(exchange, new DummyAuthEx(null))).verifyComplete();
        var tree = mapper.readTree(exchange.getResponse().getBodyAsString().block());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(tree.get("error").asText()).isNotNull();
        assertThat(tree.get("errorDescription").asText()).isNotNull();
    }
}