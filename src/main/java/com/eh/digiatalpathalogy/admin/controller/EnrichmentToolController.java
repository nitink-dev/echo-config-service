package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.services.EnrichmentToolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping(path = "api/enrichment/tools")
public class EnrichmentToolController {

    private final EnrichmentToolService toolService;


    public EnrichmentToolController(EnrichmentToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping("/{application}")
    public Mono<Map<String, Object>> getApplicationConfig(@PathVariable("application") String application) {
        return toolService.getApplicationConfig(application);
    }

    @PatchMapping("/{application}")
    public Mono<ResponseEntity<String>> updateConfigurationByPath(@PathVariable("application") String application, @RequestBody Map<String, Object> payload) {
        return toolService.updateAppConfig(application, payload)
                .thenReturn(ResponseEntity.ok("Configuration updated successfully"));
    }

}
