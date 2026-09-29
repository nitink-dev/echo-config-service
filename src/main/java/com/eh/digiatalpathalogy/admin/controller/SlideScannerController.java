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
import java.util.Set;

import static com.eh.digiatalpathalogy.admin.constant.SlideScannerFields.*;

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
    public Mono<SlideScanner> update(@PathVariable String deviceSerialNumber, @RequestBody(required = false) Map<String, Object> body) {
        Set<String> presentFields = body == null ? Set.of() : body.keySet();
        SlideScanner slideScanner = body == null ? null : toSlideScanner(body);
        return slideScannerService.updateByDeviceSerialNumber(deviceSerialNumber, slideScanner, presentFields);
    }

    private SlideScanner toSlideScanner(Map<String, Object> updates) {
        SlideScanner entity = new SlideScanner();
        if (updates.containsKey(NAME)) entity.setName((String) updates.get(NAME));
        if (updates.containsKey(MODEL)) entity.setModel((String) updates.get(MODEL));
        if (updates.containsKey(SCANNER_TYPE)) entity.setScannerType((String) updates.get(SCANNER_TYPE));
        if (updates.containsKey(LOCATION)) entity.setLocation((String) updates.get(LOCATION));
        if (updates.containsKey(DEPARTMENT)) entity.setDepartment((String) updates.get(DEPARTMENT));
        if (updates.containsKey(DICOM_STORE)) entity.setDicomStore((String) updates.get(DICOM_STORE));
        if (updates.containsKey(AE_TITLE)) entity.setAeTitle((String) updates.get(AE_TITLE));
        if (updates.containsKey(PORT)) entity.setPort((String) updates.get(PORT));
        if (updates.containsKey(HOSPITAL_NAME)) entity.setHospitalName((String) updates.get(HOSPITAL_NAME));
        if (updates.containsKey(IP_ADDRESS)) entity.setIpAddress((String) updates.get(IP_ADDRESS));
        if (updates.containsKey(VENDOR)) entity.setVendor((String) updates.get(VENDOR));
        if (updates.containsKey(RESEARCH)) entity.setResearch((Boolean) updates.get(RESEARCH));
        if (updates.containsKey(CONNECTED)) entity.setConnected((Boolean) updates.get(CONNECTED));
        if (updates.containsKey(REMOTE_AE_TITLE)) entity.setRemoteAeTitle((String) updates.get(REMOTE_AE_TITLE));
        if (updates.containsKey(REMOTE_HOST)) entity.setRemoteHost((String) updates.get(REMOTE_HOST));
        if (updates.containsKey(REMOTE_PORT)) entity.setRemotePort(toInteger(updates.get(REMOTE_PORT)));
        if (updates.containsKey(STORAGE_STRATEGY)) entity.setStorageStrategy((String) updates.get(STORAGE_STRATEGY));
        return entity;
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        String text = value.toString().trim();
        return text.isEmpty() ? null : Integer.valueOf(text);
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