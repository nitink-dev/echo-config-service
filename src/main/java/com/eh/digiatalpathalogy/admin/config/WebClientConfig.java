package com.eh.digiatalpathalogy.admin.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int RESPONSE_TIMEOUT_SEC = 5;
    private static final int READ_TIMEOUT_SEC = 5;

    private static final int MAX_CONNECTIONS = 200;
    private static final int PENDING_ACQUIRE_TIMEOUT_SEC = 5;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {

        // ✅ Connection pool configuration
        ConnectionProvider connectionProvider = ConnectionProvider.builder("http-pool")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireTimeout(Duration.ofSeconds(PENDING_ACQUIRE_TIMEOUT_SEC))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .compress(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SEC))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SEC, TimeUnit.SECONDS)));

        return builder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> {
                    log.debug("→ {} {}", request.method(), request.url());
                    return next.exchange(request);
                }).build();
    }
}