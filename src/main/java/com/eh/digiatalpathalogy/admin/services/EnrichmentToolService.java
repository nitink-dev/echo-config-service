package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.config.EnrichmentToolConfig;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.services.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

import static com.eh.digiatalpathalogy.admin.config.ConfigStore.flatMapAppMapping;
import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.ARRAY_FIELDS;

@Service
public class EnrichmentToolService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentToolService.class);

    private final ConfigurationClient configurationClient;
    private final ConfigStore configStore;
    private final EnrichmentToolConfig toolConfig;
    private final NotificationService notificationService;

    public EnrichmentToolService(ConfigurationClient configurationClient, ConfigStore configStore, EnrichmentToolConfig toolConfig, NotificationService notificationService) {
        this.configurationClient = configurationClient;
        this.configStore = configStore;
        this.toolConfig = toolConfig;
        this.notificationService = notificationService;
    }

    /**
     * Converts logical payload into category-wise (native/common) config update structure.
     */
    private Map<String, Map<String, Object>> prepareUpdateRequestPayload(EnrichmentToolConfig.AppMapping appMapping, Map<String, Object> payload) {
        return appMapping.getMappings().entrySet().stream()
                .map(categoryEntry -> {
                    Map<String, Object> inner = categoryEntry.getValue().entrySet().stream()
                            .filter(mappingEntry -> payload.containsKey(mappingEntry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getValue,
                                    mappingEntry -> handleDataType(mappingEntry.getKey(), payload.get(mappingEntry.getKey())),
                                    (a, b) -> b,
                                    LinkedHashMap::new
                            ));
                    return new AbstractMap.SimpleEntry<>(categoryEntry.getKey(), inner);
                })
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    /**
     * Converts list/array fields into comma-separated string where required.
     */
    private Object handleDataType(String key, Object value) {
        if (ARRAY_FIELDS.contains(key)) {
            if (value instanceof List<?>) {
                return ((List<?>) value).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            } else if (value.getClass().isArray()) {
                return Arrays.stream((Object[]) value)
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            }
        }
        return value;
    }

    /**
     * Validates payload keys against configured mappings (supports flattened keys).
     */
    private Mono<Boolean> isValidPayload(String application, Map<String, Object> payload) {
        if (application == null || application.isBlank()) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Application is null or blank."));
        }
        if (payload == null) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Payload cannot be null."));
        }
        if (toolConfig == null || toolConfig.getApplications() == null) {
            return Mono.error(new HttpRequestException(HttpStatus.INTERNAL_SERVER_ERROR, "toolConfig is not initialized."));
        }

        EnrichmentToolConfig.AppMapping appMapping = toolConfig.getApplications().get(application);
        if (appMapping == null) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Unknown application: " + application));
        }

        Map<String, String> mappings = flatMapAppMapping(appMapping);
        if (mappings == null || mappings.isEmpty()) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "No mappings configured for application: " + application));
        }

        Set<String> allowedKeys = mappings.keySet().stream()
                .map(k -> k.contains(".") ? k.substring(k.indexOf('.') + 1) : k)
                .collect(Collectors.toSet());

        Set<String> extraKeys = new LinkedHashSet<>(payload.keySet());
        extraKeys.removeAll(allowedKeys);

        if (!extraKeys.isEmpty()) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, String.format("Extra/unknown keys in payload for '%s': %s. Allowed keys: %s", application, extraKeys, allowedKeys)));
        }

        return Mono.just(Boolean.TRUE);
    }

    /**
     * Sends update request for a single category (native/common).
     */
    private Mono<Map<String, Object>> updateSingleCategory(String application, Map.Entry<String, Map<String, Object>> entry) {
        ConfigPayload request = new ConfigPayload(entry.getKey(), entry.getValue());
        return configurationClient.updateConfig(application, null, request);
    }

    /**
     * Handles standard application update.
     */
    private Mono<Map<String, Object>> handleGenericAppUpdate(Map<String, Map<String, Object>> updatePayload, String application) {
        return Flux.fromIterable(updatePayload.entrySet())
                .flatMapSequential(entry -> updateSingleCategory(application, entry))
                .collectList()
                .flatMap(results -> {
                    Map<String, Object> merged = new LinkedHashMap<>();
                    results.forEach(merged::putAll);
                    return configurationClient.busRefresh().thenReturn(merged);
                })
                .doOnSuccess(cp -> log.info("Configuration for '{}' updated successfully (all categories)", application))
                .doOnError(ex -> log.error("Failed to update configuration for '{}' (one or more categories)", application, ex));
    }

    /**
     * Returns config for application (aggregated or direct based on mapping).
     */
    public Mono<Map<String, Object>> getApplicationConfig(String application) {

        EnrichmentToolConfig.AppMapping appMapping = toolConfig.getApplications().get(application);
        if (appMapping == null) {
            return Mono.error(new ResourceNotFoundException("Unknown application: " + application));
        }
        boolean isAggregated = isAggregated(appMapping);
        if (isAggregated) {
            return configStore.getAggregatedConfig(application);
        }
        return configStore.getFilteredProperties(application);
    }

    private boolean isAggregated(EnrichmentToolConfig.AppMapping appMapping) {
        return appMapping.getMappings()
                .keySet()
                .stream()
                .anyMatch(toolConfig.getApplications()::containsKey);
    }

    /**
     * Updates configuration; supports both aggregated (synapse/lis) and direct apps.
     */
    public Mono<Map<String, Object>> updateAppConfig(String application, Map<String, Object> payload) {

        EnrichmentToolConfig.AppMapping appMapping = toolConfig.getApplications().get(application);
        return isValidPayload(application, payload)
                .then(getApplicationConfig(application).defaultIfEmpty(Collections.emptyMap()))
                .flatMap(oldData -> Mono.defer(() -> {
                    boolean isAggregated = isAggregated(appMapping);
                    log.info("Application='{}' isAggregated={}", application, isAggregated);

                    Mono<Map<String, Object>> updateFlow;
                    if (isAggregated) {
                        updateFlow = handleAggregatedUpdate(application, payload);
                    } else {
                        Map<String, Map<String, Object>> updatePayload = prepareUpdateRequestPayload(appMapping, payload);
                        if (updatePayload == null || updatePayload.isEmpty()) {
                            log.warn("Prepared payload empty for application='{}'", application);
                            updateFlow = Mono.just(Collections.emptyMap());
                        } else {
                            updateFlow = handleGenericAppUpdate(updatePayload, application);
                        }
                    }

                    return updateFlow.flatMap(result -> {
                        notificationService.notifyEntityChange(application, oldData, result).subscribe();

                        return configStore.deleteAllConfigKeys()
                                .doOnSuccess(v -> log.info("Cleared config cache after update for application='{}'", application))
                                .thenReturn(result);
                    });
                }));
    }

    /**
     * Handles update logic for aggregated applications (e.g. synapse, lis).
     */
    private Mono<Map<String, Object>> handleAggregatedUpdate(String application, Map<String, Object> payload) {

        Map<String, Map<String, Object>> splitPayload = splitPayloadByTargetApp(application, payload);
        log.info("Split payload for application='{}' into {} target apps", application, splitPayload.size());

        if (splitPayload.isEmpty()) {
            log.info("No valid mappings found after split for application='{}'", application);
            return Mono.just(Collections.emptyMap());
        }

        return Flux.fromIterable(splitPayload.entrySet())
                .flatMap(entry -> {
                    String targetApp = entry.getKey();
                    Map<String, Object> subPayload = entry.getValue();

                    Map<String, String> flatMapping = toolConfig.getApplications()
                            .get(application)
                            .getMappings()
                            .get(targetApp);

                    Map<String, Map<String, Object>> updatePayload = prepareAggregatedUpdatePayload(flatMapping, subPayload);
                    if (updatePayload.isEmpty()) {
                        log.warn("Prepared payload empty for targetApp='{}'", targetApp);
                        return Mono.just(Collections.<String, Object>emptyMap());
                    }
                    return handleGenericAppUpdate(updatePayload, targetApp);
                })
                .collectList()
                .map(results -> {
                    Map<String, Object> merged = new LinkedHashMap<>();
                    results.forEach(merged::putAll);
                    return merged;
                });
    }

    /**
     * Splits incoming payload into multiple target applications
     * based on mapping and returns app-wise filtered payload.
     */
    private Map<String, Map<String, Object>> splitPayloadByTargetApp(String application, Map<String, Object> payload) {

        Map<String, Map<String, String>> mappings = toolConfig.getApplications().get(application).getMappings();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, String>> entry : mappings.entrySet()) {

            String targetApp = entry.getKey();
            if (!toolConfig.getApplications().containsKey(targetApp)) {
                continue;
            }
            Map<String, String> flatMapping = entry.getValue();
            Set<String> allowedKeys = flatMapping.keySet().stream()
                    .map(k -> k.contains(".") ? k.substring(k.indexOf('.') + 1) : k)
                    .collect(Collectors.toSet());

            Map<String, Object> subPayload = payload.entrySet().stream()
                    .filter(e -> allowedKeys.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (!subPayload.isEmpty()) {
                result.put(targetApp, subPayload);
            }
        }

        return result;
    }

    /**
     * Converts flattened mapping (native.key → path)
     * into category-wise payload structure for update.
     */
    private Map<String, Map<String, Object>> prepareAggregatedUpdatePayload(Map<String, String> flatMapping, Map<String, Object> payload) {

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : flatMapping.entrySet()) {

            String fullKey = entry.getKey();
            String configPath = entry.getValue();

            String[] parts = fullKey.split("\\.", 2);

            if (parts.length != 2) continue;
            String category = parts[0];
            String logicalKey = parts[1];

            if (!payload.containsKey(logicalKey)) continue;
            result.computeIfAbsent(category, k -> new LinkedHashMap<>()).put(configPath, handleDataType(logicalKey, payload.get(logicalKey)));
        }

        return result;
    }

}
