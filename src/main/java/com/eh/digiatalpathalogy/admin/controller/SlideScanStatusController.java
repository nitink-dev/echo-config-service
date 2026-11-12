package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.PageResponse;
import com.eh.digiatalpathalogy.admin.model.scanstatus.DicomInstanceDto;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanStatusDto;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/slide-scan-status")
@Validated
public class SlideScanStatusController {

    private final SlideScanStatusService service;

    public SlideScanStatusController(SlideScanStatusService service) {
        this.service = service;
    }

    @GetMapping("/{scanStatus}")
    public Mono<PageResponse<SlideScanStatusDto>> getSlideScanByStatus(@RequestParam(defaultValue = "0") @Min(0) Integer page,
                                                                       @RequestParam(defaultValue = "50") @Min(1) Integer size,
                                                                       @PathVariable @NotBlank String scanStatus) {
        return service.getSlideScanByStatus(page, size, scanStatus);
    }

    @GetMapping(value = "/stream/in-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamSlides() {
        return service.streamInProgressSse();
    }

    @GetMapping(value = "/barcode/{barcode}")
    public Mono<SlideScanStatusDto> getSlideScanStatusByBarcode(@PathVariable String barcode) {
        return service.getSlideScanStatusByBarcode(barcode);
    }

    @GetMapping("/barcode/{barcode}/details")
    public Flux<DicomInstanceDto> getDicomInstancesByBarcode(@PathVariable @NotBlank String barcode, @RequestParam @NotBlank String seriesId) {
        return service.getDicomInstancesByBarcode(barcode,seriesId);
    }

    @GetMapping("/barcodes")
    public Mono<List<String>> getAllSlideBarcodes() {
        return service.getAllSlideBarcodes();
    }

}