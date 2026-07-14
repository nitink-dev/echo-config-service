package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.config.HealthTargetsProperties;
import com.eh.digiatalpathalogy.admin.services.HealthStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@RestController
public class HealthStatusController {

    private static final Logger log = LoggerFactory.getLogger(HealthStatusController.class);

    private final HealthTargetsProperties properties;
    private final HealthStatusService healthService;

    public HealthStatusController(HealthTargetsProperties properties, HealthStatusService healthService) {
        this.properties = properties;
        this.healthService = healthService;
    }

    @GetMapping("/api/health/status")
    public Mono<Map<String, Object>> getSystemHealthOverview() {

        log.info("Received health overview request");

        Mono<Map<String, String>> dependencies = Optional.ofNullable(healthService.resolveCoreDependencies())
                .orElseGet(() -> Mono.just(Map.of()));
        Mono<?> microservices = Mono.defer(() -> healthService.checkHttpEndpoints(Optional.ofNullable(properties.getMicroservices()).orElse(Set.of())))
                .switchIfEmpty(Mono.just(List.of()));
        Mono<?> thirdParties = Mono.defer(() -> healthService.checkIcmpEndpoints(Optional.ofNullable(properties.getThirdParties()).orElse(List.of())))
                .switchIfEmpty(Mono.just(List.of()));

        return Mono.zip(dependencies, microservices, thirdParties)
                .map(tuple -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("timestamp", Instant.now().toString());
                    response.put("dependencies", tuple.getT1());
                    response.put("microservices", tuple.getT2());
                    response.put("thirdParties", tuple.getT3());
                    return response;
                })
                .doOnSuccess(r -> log.info("Health overview response prepared"))
                .doOnError(ex -> log.error("Health overview failed", ex));
    }

}
