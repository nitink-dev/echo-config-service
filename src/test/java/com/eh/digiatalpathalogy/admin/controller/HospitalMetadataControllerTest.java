package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.HospitalMetadataDTO;
import com.eh.digiatalpathalogy.admin.services.HospitalMetadataService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.when;

@WebFluxControllerTest(controllers = HospitalMetadataController.class)
class HospitalMetadataControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private HospitalMetadataService service;

    @Test
    @DisplayName("GET /api/hospital-metadata → 200 returns hospital metadata")
    void getMetadata_success() {
        var dto = new HospitalMetadataDTO(
                List.of("NJ", "NY"),
                List.of("Hospital A", "Hospital B")
        );

        when(service.getHospitalMetadata()).thenReturn(Mono.just(dto));

        webTestClient.get()
                .uri("/api/hospital-metadata")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.locations[0]").isEqualTo("NJ")
                .jsonPath("$.locations[1]").isEqualTo("NY")
                .jsonPath("$.names[0]").isEqualTo("Hospital A")
                .jsonPath("$.names[1]").isEqualTo("Hospital B");
    }

    @Test
    @DisplayName("GET /api/hospital-metadata → 5xx when service throws error")
    void getMetadata_serviceError() {
        when(service.getHospitalMetadata()).thenReturn(Mono.error(new RuntimeException("boom")));

        webTestClient.get()
                .uri("/api/hospital-metadata")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}