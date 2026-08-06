package com.eh.digiatalpathalogy.admin.exception;

import com.eh.digiatalpathalogy.admin.util.JsonResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

public class AuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final JsonResponseWriter jsonResponseWriter;

    public AuthenticationEntryPoint(JsonResponseWriter jsonResponseWriter) {
        this.jsonResponseWriter = jsonResponseWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return exchange.getSession()
                .flatMap(session -> handleAuthenticationFailure(exchange, session));
    }

    private Mono<Void> handleAuthenticationFailure(ServerWebExchange exchange, WebSession session) {

        String error = HttpStatus.UNAUTHORIZED.getReasonPhrase();
        String errorDescription = "Authentication is required to access this resource.";
        boolean sessionExists = session != null && !session.isExpired();
        boolean securityContextMissing = session == null || session.getAttribute(WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME) == null;
        if (sessionExists && securityContextMissing) {
            error = "Session Expired";
            errorDescription = "Your session has expired. Please log in again.";
        }
        return jsonResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, error, errorDescription);
    }

}