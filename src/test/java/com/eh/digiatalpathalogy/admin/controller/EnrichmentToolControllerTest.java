package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.services.EnrichmentToolService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.Mockito.when;

@WebFluxControllerTest(controllers = EnrichmentToolController.class)
class EnrichmentToolControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EnrichmentToolService toolService;

    @Test
    @DisplayName("GET /api/enrichment/tools/{application} → 200 returns application config")
    void getApplicationConfig_success() {
        Map<String, Object> response = testEnrichmentConfig();

        when(toolService.getApplicationConfig("eh-dicom-receiver"))
                .thenReturn(Mono.just(response));

        webTestClient.get()
                .uri("/api/enrichment/tools/eh-dicom-receiver")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.aet").isEqualTo("EH_ENRICH")
                .jsonPath("$.port").isEqualTo(2575);
    }

    @Test
    @DisplayName("GET /api/enrichment/tools/{application} → 5xx when service errors")
    void getApplicationConfig_serviceError() {
        when(toolService.getApplicationConfig("eh-dicom-receiver"))
                .thenReturn(Mono.error(new RuntimeException("boom")));

        webTestClient.get()
                .uri("/api/enrichment/tools/eh-dicom-receiver")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("PATCH /api/enrichment/tools/{application} → 200 returns success message")
    void updateConfigurationByPath_success() {
        when(toolService.updateAppConfig("eh-dicom-receiver", Map.of("aet", "NEW_AET")))
                .thenReturn(Mono.just(Map.of("ok", true)));

        webTestClient.patch()
                .uri("/api/enrichment/tools/eh-dicom-receiver")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("aet", "NEW_AET"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Configuration updated successfully");
    }

    @Test
    @DisplayName("PATCH /api/enrichment/tools/{application} → 4xx/5xx when service errors")
    void updateConfigurationByPath_errorFromService() {
        when(toolService.updateAppConfig("eh-dicom-receiver", Map.of("aet", "NEW_AET")))
                .thenReturn(Mono.error(new RuntimeException("update failed")));

        webTestClient.patch()
                .uri("/api/enrichment/tools/eh-dicom-receiver")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("aet", "NEW_AET"))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    private Map<String, Object> testEnrichmentConfig() {
        return Map.of("aet", "EH_ENRICH", "port", 2575);
    }
}