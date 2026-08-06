package com.eh.digiatalpathalogy.admin.util;

import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

@Component
public class HttpRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestHandler.class);

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;

    private static final int MAX_RETRIES = 1;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(200);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(1);

    private static final Duration END_TO_END_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    public HttpRequestHandler(WebClient webClient) {
        this(webClient, defaultCircuitBreaker());
    }

    public HttpRequestHandler(WebClient webClient, CircuitBreaker circuitBreaker) {
        this.webClient = Objects.requireNonNull(webClient);
        this.circuitBreaker = Objects.requireNonNullElseGet(
                circuitBreaker,
                HttpRequestHandler::defaultCircuitBreaker
        );
    }

    private static CircuitBreaker defaultCircuitBreaker() {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .slowCallRateThreshold(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .permittedNumberOfCallsInHalfOpenState(2)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordExceptions(WebClientRequestException.class, SocketTimeoutException.class, TimeoutException.class, HttpRequestException.class)
                .ignoreExceptions(IllegalArgumentException.class, NullPointerException.class)
                .build();

        return CircuitBreakerRegistry.of(config).circuitBreaker("downstream-http");
    }

    public <T> Mono<T> request(String url, HttpMethod method, Map<String, String> queryParams, Object requestBody, Map<String, String> headers, ParameterizedTypeReference<T> responseType) {

        URI uri = buildUri(url, queryParams);
        log.info("HTTP {} → {}", method, uri);
        Mono<T> attempt = webClient.method(method)
                .uri(uri)
                .headers(h -> {
                    applyHeaders(h, headers);
                })
                .body(isBodyAllowed(method) && requestBody != null ? BodyInserters.fromValue(requestBody) : BodyInserters.empty())
                .exchangeToMono(resp -> handleResponse(resp, responseType, uri))
                .doOnSuccess(r -> log.info("Success ← {}", uri))
                .doOnError(e -> log.error("Error ← {} | {}", uri, e.getMessage()));

        return attempt
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                        .maxBackoff(MAX_BACKOFF)
                        .jitter(0.3)
                        .filter(this::isRetryableException)
                        .doBeforeRetry(rs -> log.warn("Retry {} {} attempt #{} | reason={}", method, uri, rs.totalRetries() + 1, rs.failure()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure())
                )
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .timeout(END_TO_END_TIMEOUT)
                .onErrorResume(this::handleClientException);
    }

    private <T> Mono<T> handleResponse(ClientResponse response, ParameterizedTypeReference<T> responseType, URI uri) {

        HttpStatusCode status = response.statusCode();
        if (status.value() == 503) {
            return response.createException().flatMap(Mono::error);
        }
        if (status.isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        log.error("Downstream error {} → {} | body={}", uri, status, body);
                        String userMessage;
                        if (status.is5xxServerError()) {
                            userMessage = "The service is temporarily unavailable. Please try again later.";
                        } else if (status.value() == 404) {
                            userMessage = "Requested resource was not found.";
                        } else if (status.value() == 400) {
                            userMessage = "Invalid request sent to downstream service.";
                        } else {
                            userMessage = "Something went wrong while processing your request.";
                        }
                        return Mono.error(new HttpRequestException(HttpStatus.BAD_GATEWAY, userMessage));
                    });
        }
        return response.bodyToMono(responseType);
    }

    private URI buildUri(String baseUrl, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        if (params != null) {
            params.forEach(builder::queryParam);
        }
        return builder.build(true).toUri();
    }

    private void applyHeaders(HttpHeaders headers, Map<String, String> custom) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (custom != null) {
            custom.forEach(headers::add);
        }
    }

    private boolean isBodyAllowed(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private <T> Mono<? extends T> handleClientException(Throwable ex) {

        if (ex instanceof HttpRequestException httpEx) {
            log.warn("HttpRequestException occurred: status={}, message={}", httpEx.getStatus(), httpEx.getMessage());
            return Mono.error(httpEx);
        }
        if (ex instanceof CallNotPermittedException) {
            String message = "Service temporarily unavailable (circuit breaker open)";
            log.error("Circuit breaker open: {}", ex.getMessage(), ex);
            return Mono.error(new HttpRequestException(HttpStatus.SERVICE_UNAVAILABLE, message));
        }
        if (ex instanceof WebClientRequestException reqEx) {
            String message = "Network error while calling downstream service: " + reqEx.getMessage();
            log.error(message, reqEx);
            return Mono.error(new HttpRequestException(HttpStatus.SERVICE_UNAVAILABLE, message));
        }
        if (ex instanceof WebClientResponseException resEx) {
            HttpStatus mapped = resEx.getStatusCode().is5xxServerError() ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST;
            String userMessage;
            if (resEx.getStatusCode().is5xxServerError()) {
                userMessage = "The service is currently unavailable. Please try again later.";
            } else if (resEx.getStatusCode().value() == 404) {
                userMessage = "Requested resource not found.";
            } else if (resEx.getStatusCode().value() == 400) {
                userMessage = "Invalid request. Please verify input.";
            } else {
                userMessage = "Request could not be processed.";
            }
            return Mono.error(new HttpRequestException(mapped, userMessage));
        }
        if (ex instanceof TimeoutException || ex instanceof SocketTimeoutException) {
            String message = "Request timed out while calling downstream service";
            log.error(message, ex);
            return Mono.error(new HttpRequestException(HttpStatus.GATEWAY_TIMEOUT, message));
        }
        if (ex instanceof IllegalArgumentException || ex instanceof NullPointerException) {
            String message = "Invalid request data: " + ex.getMessage();
            log.warn(message, ex);
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, message));
        }

        String message = "Unexpected internal error: " + ex.getMessage();
        log.error(message, ex);
        return Mono.error(new HttpRequestException(HttpStatus.INTERNAL_SERVER_ERROR, message));
    }

    private boolean isRetryableException(Throwable ex) {
        if (ex instanceof CallNotPermittedException) return false;
        if (ex instanceof WebClientRequestException || ex instanceof SocketTimeoutException) return true;
        if (ex instanceof WebClientResponseException res) {
            return res.getStatusCode().value() == 503;
        }
        return false;
    }
}
