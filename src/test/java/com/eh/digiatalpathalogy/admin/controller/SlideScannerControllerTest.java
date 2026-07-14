package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.services.SlideScannerService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static com.eh.digiatalpathalogy.admin.testdata.SlideScannerTestData.scanner;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebFluxControllerTest(controllers = SlideScannerController.class)
class SlideScannerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SlideScannerService slideScannerService;

    @MockitoBean
    private SlideAnalysisReportService slideAnalysisReportService;

    @Test
    @DisplayName("GET /api/scanners → 200 & list body")
    void list_scanners_ok() {
        given(slideScannerService.list())
                .willReturn(Flux.just(scanner("SS12118"), scanner("IR-983398")));

        webTestClient.get()
                .uri("/api/scanners")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(APPLICATION_JSON)
                .expectBody()
                .jsonPath("$[0].deviceSerialNumber").isEqualTo("SS12118")
                .jsonPath("$[1].deviceSerialNumber").isEqualTo("IR-983398");
    }

    @Test
    @DisplayName("GET /api/scanners/{deviceId} → 200 & body")
    void get_scanner_ok() {
        given(slideScannerService.getByDeviceSerialNumber("SS12118"))
                .willReturn(Mono.just(scanner("SS12118")));

        webTestClient.get()
                .uri("/api/scanners/SS12118")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deviceSerialNumber").isEqualTo("SS12118")
                .jsonPath("$.name").isEqualTo("SS12118");
    }

    @Test
    @DisplayName("POST /api/scanners → 201 & body")
    void create_scanner_created() {
        SlideScanner input = scanner("new-device-serial-number");
        input.setId(null);
        given(slideScannerService.create(any(SlideScanner.class)))
                .willReturn(Mono.just(scanner("new-device-serial-number")));

        webTestClient.post()
                .uri("/api/scanners")
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .bodyValue(input)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deviceSerialNumber").isEqualTo("new-device-serial-number")
                .jsonPath("$.connected").isEqualTo(true);
    }

    @Test
    @DisplayName("PATCH /api/scanners/{deviceSerialNumber} → 200 & body")
    void patch_scanner_ok() {
        SlideScanner update = new SlideScanner();
        update.setResearch(Boolean.TRUE);

        SlideScanner updated = scanner("SS12118");
        updated.setResearch(Boolean.TRUE);

        given(slideScannerService.updateByDeviceSerialNumber(eq("SS12118"), any(SlideScanner.class)))
                .willReturn(Mono.just(updated));

        webTestClient.patch()
                .uri("/api/scanners/SS12118")
                .contentType(APPLICATION_JSON)
                .accept(APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deviceSerialNumber").isEqualTo("SS12118")
                .jsonPath("$.research").isEqualTo(true);
    }

    @Test
    @DisplayName("GET /api/scanners/{id}/reports → 200 & list body")
    void get_reports_ok() {
        SlideAnalysisReport r = new SlideAnalysisReport(); // fill fields if needed
        given(slideAnalysisReportService.getReportByDeviceSerialNumber("SS12118"))
                .willReturn(Flux.just(r));

        webTestClient.get()
                .uri("/api/scanners/SS12118/reports")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0]").exists();
    }

    @Test
    @DisplayName("GET /api/scanners/datasets/dicomStores?isResearch=true → 200")
    void datasets_dicomStores_research_true_ok() {
        given(slideScannerService.fetchDatasetsWithDicomStores(true))
                .willReturn(Mono.just(Map.of("dResearch", List.of(".../dicomStores/researchStore"))));

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/scanners/datasets/dicomStores")
                        .queryParam("isResearch", "true")
                        .build())
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.dResearch[0]").exists();
    }

    @Test
    @DisplayName("DELETE /api/scanners/{deviceSerialNumber} → 200 & body true")
    void delete_ok() {
        given(slideScannerService.deleteByDeviceSerialNumber("SS12118"))
                .willReturn(Mono.just(true));

        webTestClient.delete()
                .uri("/api/scanners/SS12118")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }


    @Test
    @DisplayName("GET /api/scanners/{id} → NOT_FOUND")
    void get_scanner_not_found_controller() {
        given(slideScannerService.getByDeviceSerialNumber("unavailable-device-serial-number"))
                .willReturn(Mono.error(new ResourceNotFoundException("Slide scanner not found with DeviceSerialNumber ID: unavailable-device-serial-number")));

        webTestClient.get()
                .uri("/api/scanners/unavailable-device-serial-number")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Resource Not Found");
    }

}
