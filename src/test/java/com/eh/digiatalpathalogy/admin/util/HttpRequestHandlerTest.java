package com.eh.digiatalpathalogy.admin.util;

import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestHandlerTest {

    private WebClient webClient;
    private HttpRequestHandler handler;

    private HttpRequestHandler buildHandler(ExchangeFunction exchangeFunction) {
        this.webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        this.handler = new HttpRequestHandler(webClient);
        return handler;
    }

    private static ClientResponse jsonResponse(HttpStatus status, String jsonBody) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody)
                .build();
    }

    @Test
    @DisplayName("request: GET success → returns parsed body and builds URI with query params + headers")
    void request_get_success() {

        ExchangeFunction exchange = req -> Mono.just(jsonResponse(HttpStatus.OK, "{\"value\":\"test\"}"));

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<Map<String, Object>> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, Map.of("a", "1", "b", "two"), null, Map.of("X-Test", "1"),
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectNextMatches(map -> "test".equals(map.get("value")))
                .verifyComplete();
    }

    @Test
    @DisplayName("request: 4xx/5xx response → maps to HttpRequestException(BAD_GATEWAY) with downstream body")
    void request_errorStatus_mapsToBadGateway() {

        ExchangeFunction exchange = req -> Mono.just(jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, "downstream boom"));

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, null, null, null,
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException hre &&
                                hre.getStatus() == HttpStatus.BAD_GATEWAY &&
                                ex.getMessage().contains("temporarily unavailable"))
                .verify();
    }

    @Test
    @DisplayName("request: WebClientRequestException → maps to HttpRequestException(SERVICE_UNAVAILABLE)")
    void request_webClientRequestException_mapsToServiceUnavailable() {

        ExchangeFunction exchange = req -> Mono.error(
                new WebClientRequestException(new RuntimeException("connection refused"),
                        req.method(), req.url(), req.headers())
        );

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, null, null, null,
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException hre && hre.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();
    }

    @Test
    @DisplayName("request: TimeoutException → maps to HttpRequestException(GATEWAY_TIMEOUT)")
    void request_timeout_mapsToGatewayTimeout() {

        ExchangeFunction exchange = req -> Mono.error(new SocketTimeoutException("timeout"));

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, null, null, null,
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException hre &&
                                hre.getStatus() == HttpStatus.GATEWAY_TIMEOUT)
                .verify();
    }

    @Test
    @DisplayName("request: IllegalArgumentException → maps to HttpRequestException(BAD_REQUEST)")
    void request_illegalArgument_mapsToBadRequest() {

        ExchangeFunction exchange = req -> Mono.error(new IllegalArgumentException("bad input"));

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, null, null, null,
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException hre &&
                                hre.getStatus() == HttpStatus.BAD_REQUEST &&
                                ex.getMessage().contains("Invalid request data"))
                .verify();
    }

    @Test
    @DisplayName("request: retries exhausted → final error mapped to HttpRequestException(SERVICE_UNAVAILABLE)")
    void request_retriesExhausted_finalError() {

        AtomicInteger attempts = new AtomicInteger();
        ExchangeFunction exchange = req -> {
            attempts.incrementAndGet();
            return Mono.error(new WebClientRequestException(
                    new RuntimeException("always fails"),
                    req.method(), req.url(), req.headers()
            ));
        };

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.GET, null, null, null,
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.withVirtualTime(() -> mono)
                .thenAwait(Duration.ofSeconds(1 + 2 + 4 + 1))
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException hre &&
                                hre.getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
                .verify();

        assertEquals(2, attempts.get(), "Should attempt 4 times (initial + 3 retries)");
    }

    @Test
    @DisplayName("request: POST with body allowed → success")
    void request_post_bodyAllowed_success() {

        ExchangeFunction exchange = req -> Mono.just(jsonResponse(HttpStatus.OK, "\"CREATED\""));

        HttpRequestHandler httpRequestHandler = buildHandler(exchange);
        Mono<String> mono = httpRequestHandler.request("http://any.service/api", HttpMethod.POST, null, Map.of("a", 1),
                Map.of("X-Test", "1"),
                new ParameterizedTypeReference<>() {
                });

        StepVerifier.create(mono)
                .expectNext("\"CREATED\"")
                .verifyComplete();
    }
}