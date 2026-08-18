package com.eh.digiatalpathalogy.admin.config;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.AppConfiguration;
import com.eh.digiatalpathalogy.admin.model.ConfigPropertySource;
import com.eh.digiatalpathalogy.admin.model.HostInfo;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.*;
import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.*;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SERVICE_HOST_INFO;
import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.*;

/**
 * ConfigStore is responsible for managing configuration values.
 * It fetches configuration from a remote ConfigurationClient, caches it in Redis,
 * and provides reactive access to configuration values.
 * It supports automatic refresh on startup and lazy refresh when a key is missing.
 */
@RefreshScope
@Component
public class ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ConfigStore.class);

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final ConfigurationClient configurationClient;
    private final RedisEntityStore redisStore;
    private final ObjectMapper objectMapper;
    private final EnrichmentToolConfig toolConfig;
    private final SlideScanProgressConfig slideScanProgressConfig;
    private Set<String> validScanStatus;
    private Boolean ibexEnabled;
    private Boolean synapseEnabled;

    /**
     * Tracks in-flight refresh operations to avoid duplicate config loads
     * for the same application-profile combination.
     */
    private final Map<String, Mono<Map<String, Object>>> inFlightRefresh = new ConcurrentHashMap<>();

    public ConfigStore(ConfigurationClient configurationClient, RedisEntityStore redisStore, ObjectMapper objectMapper, EnrichmentToolConfig toolConfig, SlideScanProgressConfig slideScanProgressConfig) {
        this.configurationClient = configurationClient;
        this.redisStore = redisStore;
        this.objectMapper = objectMapper;
        this.toolConfig = toolConfig;
        this.slideScanProgressConfig = slideScanProgressConfig;
    }

    /**
     * Retrieves a configuration value from Redis or refreshes all configs if not found.
     */
    private <T> Mono<T> getInternal(String redisKey, Class<T> type, String application, String profile) {
        Map<String, String> config = configByApplication(application);
        if (config == null) {
            return Mono.error(new IllegalArgumentException("Unknown application '" + redisKey));
        }
        String path = config.get(redisKey);
        if (path == null) {
            return Mono.error(new IllegalArgumentException(
                    "Unknown redisKey '" + redisKey + "'. Register it in ConfigKeys.REDIS_TO_CONFIG_KEYS"));
        }

        return redisStore.findByKey(redisKey, type)
                .switchIfEmpty(
                        refreshAllInternal(application, profile)
                                .flatMap(all -> toTyped(all.get(redisKey), type)
                                        .switchIfEmpty(Mono.error(new NoSuchElementException(
                                                "Key '" + redisKey + "' (path '" + path + "') not found after refresh for app="
                                                        + application + ", profile=" + profile)))))
                .doOnSuccess(v -> log.debug("Resolved '{}' => {}", redisKey, v))
                .doOnError(e -> log.error("Failed to resolve key '{}' (app={}, profile={})",
                        redisKey, application, profile, e));
    }

    /**
     * Refreshes all configuration values for a given application and profile.
     * Ensures only one refresh is in-flight per app-profile combination.
     */
    private Mono<Map<String, Object>> refreshAllInternal(String application, String profile) {
        String inFlightKey = application + "|" + profile;
        Mono<Map<String, Object>> existing = inFlightRefresh.get(inFlightKey);
        if (existing != null) return existing;

        Mono<Map<String, Object>> loader = configurationClient.loadConfiguration(application)
                .flatMap(cn -> extractAllMappedValues(application, cn))
                .flatMap(values -> writeAllToRedis(values).thenReturn(values))
                .doOnSubscribe(s -> log.info("Refreshing ALL config for (app={}, profile={})", application, profile))
                .doFinally(sig -> inFlightRefresh.remove(inFlightKey))
                .cache();

        inFlightRefresh.put(inFlightKey, loader);
        return loader;
    }

    /**
     * Extracts mapped config values from the loaded configuration.
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> extractAllMappedValues(String application, Map<String, Object> config) {
        List<Map<String, Object>> propertySources =
                (List<Map<String, Object>>) config.get("propertySources");

        if (propertySources == null || propertySources.isEmpty()) {
            log.warn("No propertySources found in configuration.");
            return Mono.just(Collections.emptyMap());
        }

        Map<String, String> applicationConfig = configByApplication(application);

        Map<String, Object> pathToValue = new HashMap<>();
        for (Map<String, Object> entry : propertySources) {
            Map<String, Object> source = (Map<String, Object>) entry.get("source");
            if (source == null) continue;
            for (String path : applicationConfig.values()) {
                if (source.containsKey(path) && !pathToValue.containsKey(path)) {
                    pathToValue.put(path, source.get(path));
                }
            }
        }

        Map<String, Object> redisKeyToValue = applicationConfig.entrySet().stream()
                .filter(e -> pathToValue.containsKey(e.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> pathToValue.get(e.getValue())
                ));

        log.debug("Extracted {} mapped values from config.", redisKeyToValue.size());
        return Mono.just(redisKeyToValue);
    }

    /**
     * Writes all config values to Redis.
     */
    private Mono<Void> writeAllToRedis(Map<String, Object> redisKeyToValue) {
        if (redisKeyToValue.isEmpty()) return Mono.empty();

        return Flux.fromIterable(redisKeyToValue.entrySet())
                .filter(e -> e.getValue() != null)
                .flatMap(e -> redisStore.save(e.getKey(), e.getValue()))
                .then()
                .doOnSuccess(v -> log.info("Wrote {} entries to Redis.", redisKeyToValue.size()))
                .doOnError(e -> log.error("Failed writing entries to Redis.", e));
    }

    /**
     * Converts a raw object to the specified type using ObjectMapper.
     */
    private <T> Mono<T> toTyped(Object raw, Class<T> type) {
        if (raw == null) return Mono.empty();
        if (type.isInstance(raw)) return Mono.just(type.cast(raw));
        try {
            return Mono.just(objectMapper.convertValue(raw, type));
        } catch (IllegalArgumentException ex) {
            log.error("Type conversion failed for value '{}' to {}", raw, type.getSimpleName(), ex);
            return Mono.error(ex);
        }
    }

    /**
     * Deletes all configuration keys from Redis matching the pattern "config::*".
     */
    public Mono<Void> deleteAllConfigKeys() {
        return redisStore.deleteKeysByPattern("config:*")
                .doOnSuccess(v -> log.info("Deleted all keys with pattern config:*"))
                .doOnError(e -> log.error("Failed to delete config:* keys", e)).then();
    }

    /**
     * Refreshes all configuration values and initializes enrichment config.
     * Called on startup or bus-refresh.
     */
    public void refreshAll() {

        deleteAllConfigKeys()
                .then(refreshAllInternal(DEFAULT_APPLICATION, activeProfile)
                        .onErrorResume(ex -> {
                            log.error("Default config refresh failed", ex);
                            return Mono.empty();
                        }))
                .then(refreshEnrichmentConfig()
                        .onErrorResume(ex -> {
                            log.error("Enrichment config refresh failed", ex);
                            return Mono.just(Collections.emptyMap());
                        }))
                .subscribe(
                        v -> log.info("Configuration initialization completed"),
                        e -> log.error("Unexpected startup error", e)
                );
    }

    /**
     * Retrieves all enrichment properties for a given application.
     * If no mapping is found, returns an error.
     */
    public Mono<Map<String, Object>> getAllPropertiesForApplication(String application) throws InternalServerException {

        Map<String, EnrichmentToolConfig.AppMapping> appMappings = toolConfig.getApplications();
        EnrichmentToolConfig.AppMapping mapping = appMappings.get(application);

        if (mapping == null || mapping.getMappings() == null || mapping.getMappings().isEmpty()) {
            String errorMsg = String.format("No enrichment mappings found for application %s", application);
            log.error(errorMsg);
            return Mono.error(new ResourceNotFoundException(errorMsg));
        }

        return getPropertiesForApplication(application, mapping);
    }

    public Mono<Map<String, Object>> getFilteredProperties(String application) {

        EnrichmentToolConfig.AppMapping mapping = toolConfig.getApplications().get(application);

        if (mapping == null || mapping.getMappings() == null || mapping.getMappings().isEmpty()) {
            return Mono.error(new ResourceNotFoundException("No mappings found for application: " + application));
        }
        Set<String> expectedKeys = mapping.getMappings().values().stream()
                .filter(Objects::nonNull)
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());

        return getAllPropertiesForApplication(application)
                .map(allProps -> {
                    if (allProps == null || allProps.isEmpty()) {
                        return Collections.<String, Object>emptyMap();
                    }
                    Map<String, Object> filtered = new LinkedHashMap<>();
                    for (String key : expectedKeys) {
                        Object value = allProps.get(key);
                        if (value != null) {
                            filtered.put(key, value);
                        }
                    }
                    if (allProps.containsKey(SERVICE_IP_ADDRESS)) {
                        filtered.put(SERVICE_IP_ADDRESS, allProps.get(SERVICE_IP_ADDRESS));
                    }
                    return filtered;
                });
    }

    private final Map<String, Mono<AppConfiguration>> configCache =
            new ConcurrentHashMap<>();

    private Mono<AppConfiguration> loadConfigCached(String application) {

        return configCache.computeIfAbsent(application, key -> configurationClient
                        .loadConfig(application)
                        .timeout(Duration.ofSeconds(20))
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)).maxBackoff(Duration.ofSeconds(10)))
                        .doOnError(ex -> log.error("Failed loading config for {}", application, ex))
                        .cache()
        );
    }

    /**
     * Fetches configuration from the config service and resolves mapped values.
     * Also saves the resolved values to Redis.
     */
    private Mono<Map<String, Object>> fetchFromConfigService(String application, EnrichmentToolConfig.AppMapping mapping) {

        return loadConfigCached(application)
                .flatMap(config -> {
                    List<ConfigPropertySource> propertySources = config.getPropertySources();
                    if (propertySources == null || propertySources.isEmpty()) {
                        log.warn("No propertySources found for application '{}'", application);
                        return Mono.just(Collections.<String, Object>emptyMap());
                    }
                    Map<String, String> propMap = flatMapAppMapping(mapping);
                    Map<String, Object> resolvedValues = resolveConfigValues(config, propMap);
                    return redisStore.findByKey(SERVICE_HOST_INFO + application, HostInfo.class)
                            .map(HostInfo::ipAddress)
                            .defaultIfEmpty("N/A")
                            .flatMap(serviceIpAddress -> {
                                resolvedValues.put(SERVICE_IP_ADDRESS, serviceIpAddress);
                                return redisStore.save(String.format("config:%s", application), resolvedValues)
                                        .thenReturn(resolvedValues);
                            })
                            .map(values -> {
                                if (EH_EXPORT_SERVICE.equalsIgnoreCase(application)) {
                                    ibexEnabled = parseBoolean(values.get("ibexEnabled"));
                                    synapseEnabled = parseBoolean(values.get("synapseEnabled"));
                                    calculateScanProgressPercent(ibexEnabled, synapseEnabled);
                                }
                                return values;
                            });
                });
    }

    /**
     * Resolves config values from property sources based on provided mappings.
     */
    private Map<String, Object> resolveConfigValues(AppConfiguration config, Map<String, String> propMap) {

        Map<String, Object> resolvedValues = new HashMap<>();
        List<ConfigPropertySource> propertySources = config.getPropertySources();
        if (propMap.containsValue("name")) {
            resolvedValues.put("name", config.getName());
        }

        for (Map.Entry<String, String> mapEntry : propMap.entrySet()) {
            String property = mapEntry.getKey();
            String configPath = mapEntry.getValue();
            for (ConfigPropertySource source : propertySources) {
                Map<String, Object> sourceMap = source.getSource();
                if (sourceMap != null && sourceMap.containsKey(configPath)) {
                    Object value = sourceMap.get(configPath);
                    value = handleArrayValue(property, value);
                    resolvedValues.put(property, value);
                    break;
                }
            }
            if (!resolvedValues.containsKey(property)) {
                log.info("Property Value is not available : {}", configPath);
                resolvedValues.put(property, "test");
            }
        }
        return resolvedValues;
    }

    private Object handleArrayValue(String property, Object value) {
        if (!ARRAY_FIELDS.contains(property)) return value;
        return Objects.nonNull(value) ? value.toString().split(",") : null;
    }

    /**
     * Refreshes enrichment configuration for all mapped applications.
     * Handles both normal apps and aggregated apps (synapse/lis).
     */
    public Mono<Map<String, Map<String, Object>>> refreshEnrichmentConfig() throws InternalServerException {

        Map<String, EnrichmentToolConfig.AppMapping> appMappings = toolConfig.getApplications();

        if (appMappings == null || appMappings.isEmpty()) {
            log.warn("No enrichment mappings found.");
            return Mono.just(Collections.emptyMap());
        }

        return Flux.fromIterable(appMappings.entrySet())
                .flatMap(entry -> {
                    String application = entry.getKey();
                    EnrichmentToolConfig.AppMapping mapping = entry.getValue();
                    boolean isAggregated = mapping.getMappings()
                            .keySet()
                            .stream()
                            .anyMatch(toolConfig.getApplications()::containsKey);

                    Mono<Map<String, Object>> resultMono;
                    if (isAggregated) {
                        resultMono = getAggregatedConfig(application)
                                .doOnSuccess(r -> log.info("Aggregated config loaded for '{}'", application));
                    } else {
                        resultMono = getPropertiesForApplication(application, mapping)
                                .doOnSuccess(r -> log.info("Plain config loaded for '{}'", application));
                    }

                    return resultMono.map(props -> Map.entry(application, props));
                }, 3)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }


    /**
     * Gets enrichment properties for a specific application.
     * Tries Redis first, falls back to config service if not found.
     */
    private Mono<Map<String, Object>> getPropertiesForApplication(String application, EnrichmentToolConfig.AppMapping mapping) {
        return redisStore.fetchApplicationConfig(application)
                .flatMap(cachedValues -> {
                    if (!cachedValues.isEmpty()) {
                        return Mono.just(cachedValues);
                    }
                    return fetchFromConfigService(application, mapping);
                });
    }

    /**
     * Flattens nested enrichment mappings into a single map of property name to config path.
     */
    public static Map<String, String> flatMapAppMapping(EnrichmentToolConfig.AppMapping appMapping) {
        return appMapping.getMappings().values().stream()
                .filter(Objects::nonNull)
                .flatMap(categoryMap -> categoryMap.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ));
    }

    public Mono<String> get(String redisKey) {
        return get(redisKey, String.class, DEFAULT_APPLICATION, activeProfile);
    }

    public <T> Mono<T> get(String redisKey, Class<T> type) {
        return get(redisKey, type, DEFAULT_APPLICATION, activeProfile);
    }

    public Mono<String> get(String redisKey, String application, String profile) {
        return get(redisKey, String.class, application, profile);
    }

    public Mono<String> get(String redisKey, String application) {
        return get(redisKey, String.class, application, activeProfile);
    }

    public <T> Mono<T> get(String redisKey, Class<T> type, String application, String profile) {
        return getInternal(redisKey, type, application, profile);
    }

    /**
     * Initializes configuration on application startup or bus-refresh.
     */
    @PostConstruct
    public void initOnStartup() {
        refreshAll();
    }

    private void calculateScanProgressPercent(boolean ibexEnabled, boolean synapseEnabled) {

        Map<String, Double> progressExportConfig = slideScanProgressConfig.getServices().get(EH_DP_EXPORT_SERVICE);
        Map<String, Double> hl7ConnectorConfig = slideScanProgressConfig.getServices().get(EH_DP_HL7_CONNECTOR);
        Map<String, Double> ibexConfig = slideScanProgressConfig.getServices().get(EH_IBEX_SERVICE);
        validScanStatus = fetchValidStatuses();

        if (ibexEnabled && synapseEnabled) {
            validScanStatus.addAll(hl7ConnectorConfig.keySet());
            validScanStatus.addAll(ibexConfig.keySet());
            return;
        }

        if (ibexEnabled && !synapseEnabled) {
            progressExportConfig.put(SCAN_STATUS_EXPORTED, progressExportConfig.get(SCAN_STATUS_EXPORTED) + 20.0);
            validScanStatus.addAll(ibexConfig.keySet());
        } else if (!ibexEnabled && synapseEnabled) {
            progressExportConfig.put(SCAN_STATUS_EXPORTED, progressExportConfig.get(SCAN_STATUS_EXPORTED) + 10.0);
            validScanStatus.addAll(hl7ConnectorConfig.keySet());
        } else {
            progressExportConfig.put(SCAN_STATUS_EXPORTED, progressExportConfig.get(SCAN_STATUS_EXPORTED) + 30.0);
        }
        log.info("Slide scan progress percentage & status calculation completed successfully.");
    }

    private boolean parseBoolean(Object value) {
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private Set<String> fetchValidStatuses() {
        return slideScanProgressConfig.getServices().entrySet().stream()
                .filter(service -> !service.getKey().contains(EH_IBEX_SERVICE)
                        && !service.getKey().contains(EH_DP_HL7_CONNECTOR))
                .flatMap(service -> service.getValue().keySet().stream())
                .collect(Collectors.toSet());
    }

    public Set<String> getValidScanStatus() {
        return validScanStatus;
    }

    public Mono<Map<String, Object>> getAggregatedConfig(String application) {

        EnrichmentToolConfig.AppMapping appMapping = toolConfig.getApplications().get(application);
        return redisStore.fetchApplicationConfig(application)
                .flatMap(cachedValues -> {
                    if (cachedValues != null && !cachedValues.isEmpty()) {
                        return Mono.just(cachedValues);
                    }
                    return Flux.fromIterable(appMapping.getMappings().entrySet())
                            .filter(entry -> toolConfig.getApplications().containsKey(entry.getKey()))
                            .flatMap(entry -> {
                                String targetApp = entry.getKey();
                                Map<String, String> mapping = entry.getValue();
                                return loadConfigCached(targetApp)
                                        .map(cfg -> resolveFromConfig(cfg, mapping))
                                        .onErrorResume(ex -> {log.error("Failed loading aggregated config for {}", targetApp, ex);
                                            return Mono.just(Collections.emptyMap());
                                        });
                            })
                            .reduce(new LinkedHashMap<String, Object>(), (acc, map) -> {
                                acc.putAll(map);
                                return acc;
                            })
                            .map(acc -> (Map<String, Object>) acc)
                            .flatMap(finalResult ->
                                    redisStore.save("config:" + application, finalResult)
                                            .thenReturn(finalResult)
                            )
                            .defaultIfEmpty(Collections.<String, Object>emptyMap());
                });
    }

    /**
     * Resolves mapped values from raw configuration using flattened key mapping.
     */
    private Map<String, Object> resolveFromConfig(AppConfiguration config, Map<String, String> mapping) {

        Map<String, Object> resolved = new LinkedHashMap<>();
        List<ConfigPropertySource> sources = config.getPropertySources();

        for (Map.Entry<String, String> entry : mapping.entrySet()) {

            String fullKey = entry.getKey();
            String path = entry.getValue();
            String logicalKey = fullKey.contains(".") ? fullKey.substring(fullKey.indexOf('.') + 1) : fullKey;

            for (ConfigPropertySource source : sources) {

                Map<String, Object> sourceMap = source.getSource();

                if (sourceMap != null && sourceMap.containsKey(path)) {
                    resolved.put(logicalKey, sourceMap.get(path));
                    break;
                }
            }
        }
        return resolved;
    }

    public Boolean getIbexEnabled() {
        return ibexEnabled;
    }

    public Boolean getSynapseEnabled() {
        return synapseEnabled;
    }
}
