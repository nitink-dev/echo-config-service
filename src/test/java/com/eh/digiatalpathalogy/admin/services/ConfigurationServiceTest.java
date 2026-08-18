package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.CONFIG_SOURCE_GIT;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private ConfigurationClient configurationClient;

    @Mock
    private RedisEntityStore redisStore;

    @InjectMocks
    private ConfigurationService configurationService;

    @Test
    @DisplayName("update: update application configuration")
    void update_configuration() {

        String application = "eh-admin-console";
        Map<String, String> queryParams = Map.of("env", "dev");
        ConfigPayload payload = new ConfigPayload();
        payload.setSource(CONFIG_SOURCE_GIT);
        payload.setConfig(Map.of("path.retry-attempt", 5, "path.duration", 1));

        Map<String, Object> updatedFromServer = Map.of("status", "ok", "app", application);

        given(configurationClient.updateConfig(application, queryParams, payload)).willReturn(Mono.just(updatedFromServer));

        Mono<Map<String, Object>> result = configurationService.updateConfiguration(application, queryParams, payload);

        StepVerifier.create(result).expectNext(updatedFromServer).verifyComplete();

        verify(configurationClient, times(1)).updateConfig(application, queryParams, payload);
    }

    @Test
    @DisplayName("update: update pathQA dicom store")
    void update_path_Qa_dicomStore() {

        Map<String, String> queryParams = Map.of("region", "us-central1");
        Map<String, Object> incomingMap = Map.of(
                "gcp-config.pathqa-store-url",
                "projects/test-project/locations/us-central1/datasets/test-dataset/dicomStores/test-dicomstore"
        );

        given(configurationClient.updateConfig(isNull(), eq(queryParams), any(ConfigPayload.class)))
                .willReturn(Mono.just(Map.of("updated", true)));
        given(redisStore.deleteByKey(anyString())).willReturn(Mono.empty());

        Mono<Map<String, Object>> result = configurationService.updatePathQaDicomStore(queryParams, incomingMap);

        StepVerifier.create(result).expectNext(incomingMap).verifyComplete();

        verify(configurationClient, times(1)).updateConfig(isNull(), eq(queryParams), any(ConfigPayload.class));
        verify(redisStore, times(1)).deleteByKey(anyString());
    }


    @Test
    @DisplayName("update: error while updating application configuration")
    void update_configuration_error() {

        String application = "eh-admin-console";

        RuntimeException ex = new RuntimeException("Error while configuration update");
        given(configurationClient.updateConfig(any(), anyMap(), any())).willReturn(Mono.error(ex));

        Mono<Map<String, Object>> result = configurationService.updateConfiguration(application, Map.of(), new ConfigPayload());

        StepVerifier.create(result).expectErrorMatches(e -> e == ex).verify();

        verify(configurationClient).updateConfig(any(), anyMap(), any());
        verify(configurationClient, never()).busRefresh();
        verifyNoInteractions(redisStore);
    }

}
