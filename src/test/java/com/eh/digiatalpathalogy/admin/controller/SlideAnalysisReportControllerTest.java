package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.eh.digiatalpathalogy.admin.testdata.SlideAnalysisReportTestData.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxControllerTest(controllers = SlideAnalysisReportController.class)
class SlideAnalysisReportControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SlideAnalysisReportService slideAnalysisReportService;

    @Test
    @DisplayName("GET /api/slide-analysis/{analysisId} -> 200 OK when report exists")
    void getAnalysisById_whenFound_returnsOk() {

        given(slideAnalysisReportService.getAnalysisById(ANALYSIS_ID))
                .willReturn(Mono.just(analysisReport()));

        webTestClient.get()
                .uri("/api/slide-analysis/{analysisId}", ANALYSIS_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.analysisId").isEqualTo(ANALYSIS_ID)
                .jsonPath("$.status").isEqualTo("Completed");
    }

    @Test
    @DisplayName("GET /api/slide-analysis/{analysisId} -> 404 Not Found when report missing")
    void getAnalysisById_whenMissing_returnsNotFound() {

        given(slideAnalysisReportService.getAnalysisById(MISSING_ANALYSIS_ID))
                .willReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/slide-analysis/{analysisId}", MISSING_ANALYSIS_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /api/slide-analysis/device/{deviceSerialNumber} -> 200 OK with list")
    void getAnalysesByDeviceId_returnsFlux() {

        SlideAnalysisReport report1 = analysisReport("report-1");
        SlideAnalysisReport report2 = analysisReport("report-2");


        given(slideAnalysisReportService.getReportByDeviceSerialNumber(DEVICE_SERIAL_NUMBER))
                .willReturn(Flux.just(report1, report2));

        webTestClient.get()
                .uri("/api/slide-analysis/device/{deviceSerialNumber}", DEVICE_SERIAL_NUMBER)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$[0].analysisId").isEqualTo("report-1")
                .jsonPath("$[1].analysisId").isEqualTo("report-2");
    }

    @Test
    @DisplayName("POST /api/slide-analysis/submit -> 200 OK and success message")
    void submitAnalysis_returnsSuccessMessage() {

        given(slideAnalysisReportService.processAnalysisRequest(kafkaMessage))
                .willReturn(Mono.empty());

        webTestClient.post()
                .uri("/api/slide-analysis/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(kafkaMessage)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Analysis request submitted successfully");

        verify(slideAnalysisReportService).processAnalysisRequest(kafkaMessage);
    }
}