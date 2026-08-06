package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.services.SlideScannerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "api/scanners")
public class SlideScannerController {

    private final SlideScannerService slideScannerService;
    private final SlideAnalysisReportService slideAnalysisReportService;

    public SlideScannerController(SlideScannerService slideScannerService, SlideAnalysisReportService slideAnalysisReportService) {
        this.slideScannerService = slideScannerService;
        this.slideAnalysisReportService = slideAnalysisReportService;
    }

    @GetMapping
    public Flux<SlideScanner> list() {
        return slideScannerService.list();
    }

    @GetMapping("{deviceId}")
    public Mono<SlideScanner> get(@PathVariable String deviceId) {
        return slideScannerService.getByDeviceSerialNumber(deviceId);
    }

    @PostMapping
    public Mono<SlideScanner> create(@Valid @RequestBody SlideScanner slideScanner) {
        return slideScannerService.create(slideScanner);
    }

    @PatchMapping("{deviceSerialNumber}")
    public Mono<SlideScanner> update(@PathVariable String deviceSerialNumber, @RequestBody SlideScanner slideScanner) {
        return slideScannerService.updateByDeviceSerialNumber(deviceSerialNumber, slideScanner);
    }

    @GetMapping("{id}/reports")
    public Flux<SlideAnalysisReport> reports(@PathVariable String id) {
        return slideAnalysisReportService.getReportByDeviceSerialNumber(id);
    }

    @GetMapping("/datasets/dicomStores")
    public Mono<Map<String, List<String>>> fetchDatasetsWithDicomStores(@RequestParam(required = false) Boolean isResearch) {
        return slideScannerService.fetchDatasetsWithDicomStores(isResearch);
    }

    @DeleteMapping("/{deviceSerialNumber}")
    public Mono<Boolean> deleteByDeviceSerialNumber(@PathVariable String deviceSerialNumber) {
        return slideScannerService.deleteByDeviceSerialNumber(deviceSerialNumber);
    }

}
