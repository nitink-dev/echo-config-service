package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.services.ConfigurationService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxControllerTest(controllers = ConfigurationController.class)
class ConfigurationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ConfigurationService configurationService;

    @Test
    @DisplayName("PATCH /api/config → 200 returns saved user")
    void update_success() {

        ConfigPayload payload = new ConfigPayload();
        payload.setSource("native");
        payload.setConfig(Map.of("path.qa-baseurl", "http://localhost:8009/api/slide/test"));

        given(configurationService.updateConfiguration(isNull(), anyMap(), any(ConfigPayload.class)))
                .willReturn(Mono.just(Map.of("updated", true)));

        webTestClient.patch()
                .uri("/api/config") // no query params
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Configuration updated successfully");

        verify(configurationService).updateConfiguration(any(), anyMap(), any());
    }

    @Test
    @DisplayName("PATCH /api/config/{application} → 200 returns updates Application config")
    void update_application_config_success() {

        ConfigPayload payload = new ConfigPayload();
        payload.setSource("native");
        payload.setConfig(Map.of("path.retry-attempt", 5, "path.duration", 1));

        given(configurationService.updateConfiguration(eq("eh-admin-console"), anyMap(), any(ConfigPayload.class)))
                .willReturn(Mono.just(Map.of("updated", true)));

        webTestClient.patch()
                .uri("/api/config/eh-admin-console")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Configuration updated successfully");

        verify(configurationService).updateConfiguration(eq("eh-admin-console"), anyMap(), any(ConfigPayload.class));
    }

    @Test
    @DisplayName("PATCH /api/config/path-qa/dicom-store → 200 update pathQA dicom store")
    void update_dicomStore_success() {

        Map<String, Object> body = Map.of(
                "gcp-config.pathqa-store-url",
                "projects/test-project/locations/us-central1/datasets/test-dataset/dicomStores/test-dicomstore"
        );

        given(configurationService.updatePathQaDicomStore(anyMap(), anyMap()))
                .willReturn(Mono.just(body));

        webTestClient.patch()
                .uri("/api/config/path-qa/dicom-store") // no query params
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.['gcp-config.pathqa-store-url']").isEqualTo(
                        "projects/test-project/locations/us-central1/datasets/test-dataset/dicomStores/test-dicomstore"
                );

        verify(configurationService).updatePathQaDicomStore(anyMap(), eq(body));

    }
}