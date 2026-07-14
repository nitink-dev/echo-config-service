package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.HealthTargetsProperties;
import com.eh.digiatalpathalogy.admin.model.HealthStatusResult;
import com.eh.digiatalpathalogy.admin.model.HostInfo;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.ReactiveHealthContributor;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SERVICE_HOST_INFO;


@Service
public class HealthStatusService {

    private static final Logger log = LoggerFactory.getLogger(HealthStatusService.class);

    private final WebClient webClient;
    private final HealthTargetsProperties properties;
    private final ReactiveHealthContributorRegistry registry;
    private final RedisEntityStore redisEntityStore;

    public HealthStatusService(HealthTargetsProperties properties, ReactiveHealthContributorRegistry registry, RedisEntityStore redisEntityStore) {
        this.webClient = buildWebClient(properties);
        this.properties = properties;
        this.registry = registry;
        this.redisEntityStore = redisEntityStore;
    }

    public Mono<List<HealthStatusResult>> checkHttpEndpoints(Set<HostInfo> targets) {

        log.info("Starting HTTP health checks for {} endpoints", targets.size());

        return Flux.fromIterable(targets)
                .flatMap(this::checkHttpEndpoint, 8)
                .collectList()
                .doOnSuccess(r -> log.info("Completed HTTP health checks"))
                .doOnError(ex -> log.error("HTTP health checks failed", ex));
    }

    private Mono<HealthStatusResult> checkHttpEndpoint(HostInfo configureHostInfo) {

        String serviceName = configureHostInfo.serviceName();
        String displayName = configureHostInfo.displayName();
        String key = SERVICE_HOST_INFO + serviceName;

        log.info("Starting health check for service={}", serviceName);

        return redisEntityStore.findByKey(key, HostInfo.class)
                .map(hostInfo -> new HostInfo(hostInfo.ipAddress(), hostInfo.port(), configureHostInfo.serviceName(), configureHostInfo.displayName()))
                .flatMap(this::callHealthApi)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    String message = "Service is not registered or not ready";
                    log.warn("Health check skipped. service={}, reason={}", serviceName, message);
                    return new HealthStatusResult().down(displayName, null, null, 0, message);
                }));
    }


    private Mono<HealthStatusResult> callHealthApi(HostInfo hostInfo) {
        Instant start = Instant.now();
        String applicationName = hostInfo.displayName();
        String healthUrl = buildHealthUrl(hostInfo);
        log.debug("Checking HTTP endpoint: name={}, url={}", applicationName, healthUrl);
        return webClient.get()
                .uri(healthUrl)
                .exchangeToMono(resp -> {
                    long latency = Duration.between(start, Instant.now()).toMillis();
                    boolean up = resp.statusCode().is2xxSuccessful();
                    log.debug("HTTP check result: name={}, status={}, latency={}ms", applicationName, resp.statusCode().value(), latency);
                    return Mono.just(new HealthStatusResult(up ? "UP" : "DOWN", healthUrl, applicationName, resp.statusCode().value(), latency, null));
                })
                .timeout(properties.getReadTimeout())
                .onErrorResume(ex -> {
                    long latency = Duration.between(start, Instant.now()).toMillis();
                    log.warn("HTTP check failed: name={}, url={}, reason={}", applicationName, healthUrl, ex.getMessage());
                    return Mono.just(new HealthStatusResult().down(applicationName, healthUrl, null, latency, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                });
    }


    public Mono<List<HealthStatusResult>> checkIcmpEndpoints(List<HealthTargetsProperties.Target> targets) {
        log.info("Starting ICMP health checks for {} hosts", targets.size());
        return Flux.fromIterable(targets)
                .flatMap(this::checkIcmpEndpoint, 4)
                .collectList()
                .doOnSuccess(r -> log.info("Completed ICMP health checks"))
                .doOnError(ex -> log.error("ICMP health checks failed", ex));
    }

    private Mono<HealthStatusResult> checkIcmpEndpoint(HealthTargetsProperties.Target target) {
        return Mono.fromCallable(() -> executeBlockingIcmpPing(target))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private HealthStatusResult executeBlockingIcmpPing(HealthTargetsProperties.Target target) {

        String host = resolveHost(target.getUrl());
        long timeoutSeconds = Math.max(1, properties.getReadTimeout().toSeconds());

        Instant start = Instant.now();

        log.debug("Starting ICMP ping: name={}, host={}", target.getName(), host);

        try {
            Process process = new ProcessBuilder("ping", "-c", "1", "-W", String.valueOf(timeoutSeconds), host).start();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long latency = Duration.between(start, Instant.now()).toMillis();
            if (!completed) {
                process.destroyForcibly();
                log.warn("ICMP timeout: name={}, host={}", target.getName(), host);
                return new HealthStatusResult().down(target.getName(), target.getUrl(), null, latency, "Timeout");
            }
            if (process.exitValue() == 0) {
                log.debug("ICMP UP: name={}, host={}, latency={}ms", target.getName(), host, latency);
                return new HealthStatusResult("UP", target.getUrl(), target.getName(), 0, latency, null);
            }
            String error = readProcessOutput(process);
            log.warn("ICMP failed: name={}, host={}, reason={}", target.getName(), host, error);
            return new HealthStatusResult().down(target.getName(), target.getUrl(), null, latency, error);

        } catch (Exception ex) {
            long latency = Duration.between(start, Instant.now()).toMillis();
            log.error("ICMP ping exception: name={}, host={}", target.getName(), host, ex);
            return new HealthStatusResult().down(target.getName(), target.getUrl(), null, latency, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String readProcessOutput(Process process) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return br.readLine();
        } catch (Exception e) {
            return "ICMP output unavailable";
        }
    }

    private String resolveHost(String value) {

        if (!StringUtils.hasText(value)) {
            log.warn("resolveHost called with empty or null value");
            return value;
        }
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null) {
                log.debug("Resolved host '{}' from value '{}'", uri.getHost(), value);
                return uri.getHost();
            }
            log.debug("No host part found in URI, using raw value: {}", value);
            return value;

        } catch (IllegalArgumentException ex) {
            log.warn("Failed to parse value '{}' as URI, using raw value instead: {}", value, ex.getMessage());
            return value;
        }
    }

    private WebClient buildWebClient(HealthTargetsProperties props) {

        HttpClient client = HttpClient.create()
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) props.getConnectionTimeout().toMillis()
                )
                .responseTimeout(props.getReadTimeout());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }

    public Mono<Map<String, String>> resolveCoreDependencies() {
        log.debug("Resolving core infrastructure dependencies");
        return Flux.fromIterable(registry)
                .flatMap(entry -> {
                    ReactiveHealthContributor contributor = entry.getContributor();
                    String name = entry.getName();
                    if (contributor instanceof ReactiveHealthIndicator rhi) {
                        return rhi.health()
                                .map(h -> Map.entry(name, h.getStatus().getCode()))
                                .onErrorReturn(Map.entry(name, "UNKNOWN"));
                    }
                    return Mono.empty();
                })
                .filter(e -> e.getKey().contains("mongo") || e.getKey().contains("redis") || e.getKey().contains("kafka"))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .cache();
    }

    public String buildHealthUrl(HostInfo hostInfo) {
        return "http://" + hostInfo.ipAddress() + ":" + hostInfo.port() + "/actuator/health";
    }
}