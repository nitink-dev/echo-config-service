package com.eh.digiatalpathalogy.admin.exception;

import com.eh.digiatalpathalogy.admin.util.JsonResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CsrfException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

public class AccessDeniedHandler implements ServerAccessDeniedHandler {
    
    private final JsonResponseWriter jsonResponseWriter;

    public AccessDeniedHandler(JsonResponseWriter jsonResponseWriter) {
        this.jsonResponseWriter = jsonResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        return exchange.getSession()
                .flatMap(session -> determineResponse(exchange, session, ex));
    }

    private Mono<Void> determineResponse(ServerWebExchange exchange, WebSession session, AccessDeniedException ex) {

        boolean hasSession = session != null && session.isStarted() && session.getAttributes().containsKey(
                WebSessionServerSecurityContextRepository.DEFAULT_SPRING_SECURITY_CONTEXT_ATTR_NAME);
        if (!hasSession) {
            return jsonResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required to access this resource.");
        }
        if (ex instanceof CsrfException) {
            return jsonResponseWriter.write(exchange, HttpStatus.FORBIDDEN, "Access Denied", "Your CSRF token is invalid. Please refresh session.");
        }
        return jsonResponseWriter.write(exchange, HttpStatus.FORBIDDEN, "Access Denied", "You do not have the required permissions to access this resource.");
    }
}