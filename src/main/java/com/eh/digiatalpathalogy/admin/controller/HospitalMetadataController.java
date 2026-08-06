package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.HospitalMetadataDTO;
import com.eh.digiatalpathalogy.admin.services.HospitalMetadataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/hospital-metadata")
public class HospitalMetadataController {

    private final HospitalMetadataService service;

    public HospitalMetadataController(HospitalMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<HospitalMetadataDTO> getMetadata() {
        return service.getHospitalMetadata();
    }
}
