package com.eh.digiatalpathalogy.admin.exception;

import com.eh.digiatalpathalogy.admin.util.JsonResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class SystemFailureWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SystemFailureWebExceptionHandler.class);

    private final JsonResponseWriter jsonResponseWriter;

    public SystemFailureWebExceptionHandler(JsonResponseWriter jsonResponseWriter) {
        this.jsonResponseWriter = jsonResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        if (isRedisFailure(ex)) {
            log.error("Redis session infrastructure failure. path={}, method={}", exchange.getRequest().getPath(), exchange.getRequest().getMethod(), ex);
            return jsonResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Session Service Unavailable",
                    "Authentication service temporarily unavailable. Please try again.");
        }

        return Mono.error(ex);
    }

    private boolean isRedisFailure(Throwable t) {
        return t instanceof io.lettuce.core.RedisException || t instanceof RedisConnectionFailureException || (t.getMessage() != null && t.getMessage().contains("Currently not connected"));
    }

}