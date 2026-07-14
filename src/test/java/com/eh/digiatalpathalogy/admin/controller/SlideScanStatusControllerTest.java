package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.PageResponse;
import com.eh.digiatalpathalogy.admin.model.scanstatus.DicomInstanceDto;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanStatusDto;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.eh.digiatalpathalogy.admin.testdata.SlideScanStatusTestData.dicomEnrichedDto;
import static com.eh.digiatalpathalogy.admin.testdata.SlideScanStatusTestData.slideScanStatus;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@WebFluxControllerTest(controllers = SlideScanStatusController.class)
class SlideScanStatusControllerTest {

    @MockitoBean
    private SlideScanStatusService service;
    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("GET /api/slide-scan-status/{scanStatus} returns paged results")
    void getSlideScanByStatus_ok() {

        PageResponse<SlideScanStatusDto> page = PageResponse.of(List.of(slideScanStatus("120224", "in-progress")), 0, 50, 1L);

        given(service.getSlideScanByStatus(0, 50, "in-progress")).willReturn(Mono.just(page));
        webTestClient.get()
                .uri("/api/slide-scan-status/{scanStatus}?page=0&size=50", "in-progress")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PageResponse.class)
                .consumeWith(b -> verify(service).getSlideScanByStatus(0, 50, "in-progress"));
    }

    @Test
    @DisplayName("GET /api/slide-scan-status/stream/in-progress streams SSE")
    void stream_in_progress_sse_ok() {

        String ssePayload = "{\"id\":\"698b80f11dcde395dde85969\",\"accessionNumber\":null,\"slideBarcode\":\"395GHV\",\"deviceSerialNumber\":\"SS35106\",\"scanStatus\":\"enrichment-in-progress\",\"progressPercent\":2.0,\"createdAt\":\"2026-02-10T19:03:13.346Z\",\"updatedAt\":\"2026-02-10T19:03:13.346Z\"}";
        ServerSentEvent<Object> sse = ServerSentEvent.builder().id("395GHV").event("slide_scan_status").data(ssePayload).build();
        given(service.streamInProgressSse()).willReturn(Flux.just(sse));

        webTestClient.get()
                .uri("/api/slide-scan-status/stream/in-progress")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(String.class)
                .hasSize(1);

        verify(service).streamInProgressSse();
    }

    @Test
    @DisplayName("GET /api/slide-scan-status/barcode/{barcode} returns DTO")
    void get_by_barcode_ok() {
        String slideBarcode = "120324";
        SlideScanStatusDto dto = slideScanStatus(slideBarcode, "completed");
        given(service.getSlideScanStatusByBarcode(slideBarcode)).willReturn(Mono.just(dto));

        webTestClient.get()
                .uri("/api/slide-scan-status/barcode/{barcode}", slideBarcode)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SlideScanStatusDto.class).isEqualTo(dto);

        verify(service).getSlideScanStatusByBarcode(slideBarcode);
    }

    @Test
    @DisplayName("GET /api/slide-scan-status/barcode/{barcode}/details returns DICOM instances")
    void get_dicom_instances_ok() {

        String slideBarcode = "120324";
        String seriesId = "1.1.1.00.123.1234";
        DicomInstanceDto dicomInstance = dicomEnrichedDto(slideBarcode);
        given(service.getDicomInstancesByBarcode(slideBarcode,seriesId)).willReturn(Flux.just(dicomInstance));
        webTestClient.get()
                .uri("/api/slide-scan-status/barcode/{barcode}/details?seriesId="+seriesId, slideBarcode)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(DicomInstanceDto.class)
                .contains(dicomInstance);

        verify(service).getDicomInstancesByBarcode(slideBarcode,seriesId);
    }

    @Test
    @DisplayName("GET /api/slide-scan-status/barcodes returns list")
    void get_all_barcodes_ok() {

        given(service.getAllSlideBarcodes()).willReturn(Mono.just(List.of("36J5C2", "36J5C4")));
        webTestClient.get()
                .uri("/api/slide-scan-status/barcodes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0]").isEqualTo("36J5C2")
                .jsonPath("$[1]").isEqualTo("36J5C4")
                .jsonPath("$.length()").isEqualTo(2);

        verify(service).getAllSlideBarcodes();
    }
}