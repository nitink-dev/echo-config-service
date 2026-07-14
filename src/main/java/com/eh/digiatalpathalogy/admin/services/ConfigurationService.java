package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.PATH_QA_DICOM_STORE;

@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private final ConfigurationClient configurationClient;
    private final RedisEntityStore redisStore;

    public ConfigurationService(ConfigurationClient configurationClient, RedisEntityStore redisStore) {
        this.configurationClient = configurationClient;
        this.redisStore = redisStore;
    }

    public Mono<Map<String, Object>> updateConfiguration(String application,
                                                         Map<String, String> queryParams,
                                                         ConfigPayload config) {
        return configurationClient.updateConfig(application, queryParams, config)
                .flatMap(updated -> configurationClient.busRefresh()
                        .thenReturn(updated))
                .doOnSuccess(updated -> log.info("Configuration for '{}' updated successfully", application))
                .doOnError(error -> log.error("Failed to update configuration for '{}'", application, error));
    }

    public Mono<Map<String, Object>> updatePathQaDicomStore(Map<String, String> queryParams, Map<String, Object> config) {
        ConfigPayload configPayload = new ConfigPayload();
        configPayload.setSource("native");
        configPayload.setConfig(config);
        configPayload.setSource("common");
        return updateConfiguration(null, queryParams, configPayload)
                .then(redisStore.deleteByKey(PATH_QA_DICOM_STORE))
                .flatMap(updated -> configurationClient.busRefresh()
                        .thenReturn(updated))
                .thenReturn(config);
    }

}
