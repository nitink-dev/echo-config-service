package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "api/slide-analysis")
public class SlideAnalysisReportController {

    private final SlideAnalysisReportService slideAnalysisReportService;

    public SlideAnalysisReportController(SlideAnalysisReportService slideAnalysisReportService) {
        this.slideAnalysisReportService = slideAnalysisReportService;
    }

    @GetMapping("/{analysisId}")
    public Mono<ResponseEntity<SlideAnalysisReport>> getAnalysisById(@PathVariable String analysisId) {
        return slideAnalysisReportService.getAnalysisById(analysisId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/device/{deviceSerialNumber}")
    public Flux<SlideAnalysisReport> getAnalysesByDeviceId(@PathVariable String deviceSerialNumber) {
        return slideAnalysisReportService.getReportByDeviceSerialNumber(deviceSerialNumber);
    }

    @PostMapping("/submit")
    public Mono<String> submitAnalysis(@RequestBody SlideAnalysisMessage request) {
        return slideAnalysisReportService.processAnalysisRequest(request)
                .then(Mono.just("Analysis request submitted successfully"));
    }

}

