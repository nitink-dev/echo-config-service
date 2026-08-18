package com.eh.digiatalpathalogy.admin.services;


import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.config.EnrichmentToolConfig;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static com.eh.digiatalpathalogy.admin.testdata.EnrichmentToolTestData.buildRealConfig;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrichmentToolServiceTest {

    @Mock
    private ConfigurationClient configurationClient;

    @Mock
    private ConfigStore configStore;

    @Mock
    private NotificationService notificationService;

    private EnrichmentToolService service;

    @BeforeEach
    void setUp() {
        EnrichmentToolConfig toolConfig = buildRealConfig();
        service = new EnrichmentToolService(configurationClient, configStore, toolConfig, notificationService);
    }

    @Test
    @DisplayName("getApplicationConfig: generic app → returns filtered config")
    void getApplicationConfig_genericApp_returnsFilteredConfig() {

        when(configStore.getFilteredProperties("eh-dicom-receiver"))
                .thenReturn(Mono.just(Map.of("aet", "EH_ENRICH")));

        StepVerifier.create(service.getApplicationConfig("eh-dicom-receiver"))
                .expectNextMatches(m -> "EH_ENRICH".equals(m.get("aet")))
                .verifyComplete();
    }

    @Test
    @DisplayName("getApplicationConfig: aggregated app → returns aggregated config")
    void getApplicationConfig_aggregatedApp_returnsAggregatedConfig() {

        when(configStore.getAggregatedConfig("synapse"))
                .thenReturn(Mono.just(Map.of("receivingAppName", "APP1")));

        StepVerifier.create(service.getApplicationConfig("synapse"))
                .expectNextMatches(m -> m.containsKey("receivingAppName"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getApplicationConfig: unknown app → throws ResourceNotFoundException")
    void getApplicationConfig_unknownApp_throwsException() {

        StepVerifier.create(service.getApplicationConfig("invalid"))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("updateAppConfig: blank application → returns BAD_REQUEST")
    void updateAppConfig_blankApplication_returnsError() {

        StepVerifier.create(service.updateAppConfig(" ", Map.of()))
                .expectError(HttpRequestException.class)
                .verify();
    }

    @Test
    @DisplayName("updateAppConfig: null payload → returns BAD_REQUEST")
    void updateAppConfig_nullPayload_returnsError() {

        StepVerifier.create(service.updateAppConfig("eh-dicom-receiver", null))
                .expectError(HttpRequestException.class)
                .verify();
    }

    @Test
    @DisplayName("updateAppConfig: unknown application → returns BAD_REQUEST")
    void updateAppConfig_unknownApplication_returnsError() {

        StepVerifier.create(service.updateAppConfig("invalid", Map.of("aet", "X")))
                .expectError(HttpRequestException.class)
                .verify();
    }

    @Test
    @DisplayName("updateAppConfig: payload contains extra keys → validation fails")
    void updateAppConfig_extraKeys_returnsError() {

        StepVerifier.create(service.updateAppConfig(
                        "eh-dicom-receiver",
                        Map.of("aet", "X", "invalid", "oops")))
                .expectErrorMatches(e ->
                        e instanceof HttpRequestException &&
                                e.getMessage().contains("Extra/unknown keys"))
                .verify();
    }

    @Test
    @DisplayName("updateAppConfig: generic app → success + cache cleared")
    void updateAppConfig_genericApp_success() {

        when(configStore.getFilteredProperties("eh-dicom-receiver"))
                .thenReturn(Mono.just(Map.of()));

        when(configurationClient.updateConfig(eq("eh-dicom-receiver"), isNull(), any()))
                .thenReturn(Mono.just(Map.of("storescp.aetitle", "NEW_AET")));
        when(configStore.deleteAllConfigKeys()).thenReturn(Mono.empty());

        StepVerifier.create(service.updateAppConfig(
                        "eh-dicom-receiver",
                        Map.of("aet", "NEW_AET", "port", 11112)))
                .expectNextMatches(m -> m.containsKey("storescp.aetitle"))
                .verifyComplete();

        verify(configStore).deleteAllConfigKeys();
    }

    @Test
    @DisplayName("updateAppConfig: generic app → empty payload results in empty response")
    void updateAppConfig_genericApp_emptyPayload() {

        when(configStore.getFilteredProperties("eh-dicom-receiver"))
                .thenReturn(Mono.just(Map.of()));

        when(notificationService.notifyEntityChange(anyString(), anyMap(), anyMap()))
                .thenReturn(Mono.empty());

        when(configStore.deleteAllConfigKeys()).thenReturn(Mono.empty());

        StepVerifier.create(service.updateAppConfig("eh-dicom-receiver", Map.of()))
                .expectNextMatches(Map::isEmpty)
                .verifyComplete();
    }

    @Test
    @DisplayName("updateAppConfig: synapse → routes payload and updates mapped applications")
    void updateAppConfig_synapse_success() {

        when(configStore.getAggregatedConfig("synapse"))
                .thenReturn(Mono.just(Map.of()));

        when(configurationClient.updateConfig(anyString(), isNull(), any()))
                .thenReturn(Mono.just(Map.of("ok", true)));
        when(configStore.deleteAllConfigKeys()).thenReturn(Mono.empty());

        StepVerifier.create(service.updateAppConfig("synapse", Map.of(
                        "receivingAppName", "APP1",
                        "synapseServerFolder", "/data"
                )))
                .expectNextCount(1)
                .verifyComplete();

        verify(configurationClient, atLeastOnce()).updateConfig(eq("eh-hl7-connector"), isNull(), any());
        verify(configurationClient, atLeastOnce()).updateConfig(eq("eh-export-service"), isNull(), any());
        verify(configStore).deleteAllConfigKeys();
    }

    @Test
    @DisplayName("updateAppConfig: array field → converts list to CSV string")
    void updateAppConfig_arrayField_convertedToCsv() {

        when(configStore.getFilteredProperties("eh-email-service"))
                .thenReturn(Mono.just(Map.of()));

        when(configurationClient.updateConfig(anyString(), isNull(), any()))
                .thenReturn(Mono.just(Map.of("key", "value")));
        when(configStore.deleteAllConfigKeys()).thenReturn(Mono.empty());

        StepVerifier.create(service.updateAppConfig(
                        "eh-email-service",
                        Map.of("emailTo", List.of("a@test.com", "b@test.com"))
                ))
                .expectNextCount(1)
                .verifyComplete();
    }

}