package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.PATH_QA_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.CONFIG_SOURCE_GIT;

@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationClient configurationClient;
    private final RedisEntityStore redisStore;
    private final NotificationService notificationService;
    private final ConfigStore configStore;

    public ConfigurationService(ConfigurationClient configurationClient, RedisEntityStore redisStore, NotificationService notificationService, ConfigStore configStore) {
        this.configurationClient = configurationClient;
        this.redisStore = redisStore;
        this.notificationService = notificationService;
        this.configStore = configStore;
    }

    public Mono<Map<String, Object>> updateConfiguration(String application,
                                                         Map<String, String> queryParams,
                                                         ConfigPayload config) {
        return configurationClient.updateConfig(application, queryParams, config)
                .doOnSuccess(updated -> log.info("Configuration for '{}' updated successfully", application))
                .doOnError(error -> log.error("Failed to update configuration for '{}'", application, error));
    }

    public Mono<Map<String, Object>> updatePathQaDicomStore(Map<String, String> queryParams, Map<String, Object> config) {
        ConfigPayload configPayload = new ConfigPayload();
        configPayload.setSource(CONFIG_SOURCE_GIT);
        configPayload.setConfig(config);
        configPayload.setSource("common");
        return configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION)
                .cast(Object.class)
                .map(Optional::of)
                .onErrorReturn(Optional.empty())
                .flatMap(oldValue -> updateConfiguration(null, queryParams, configPayload)
                        .then(redisStore.deleteByKey(PATH_QA_DICOM_STORE))
                        .then(notificationService.notifyEntityChange("dicomStore", oldValue.orElse(null), config))
                        .thenReturn(config));
    }

}