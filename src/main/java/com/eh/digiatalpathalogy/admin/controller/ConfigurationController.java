package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.services.ConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping(path = "api/config")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    public ConfigurationController(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @PatchMapping("/{application}")
    public Mono<ResponseEntity<String>> updateConfigurationByPath(@PathVariable("application") String application, @RequestParam Map<String, String> queryParams, @RequestBody ConfigPayload config) {
        return configurationService.updateConfiguration(application, queryParams, config)
                .thenReturn(ResponseEntity.ok("Configuration updated successfully"));
    }

    @PatchMapping
    public Mono<ResponseEntity<String>> updateConfiguration(@RequestParam Map<String, String> queryParams, @Valid @RequestBody ConfigPayload config) {
        return configurationService.updateConfiguration(null, queryParams, config)
                .thenReturn(ResponseEntity.ok("Configuration updated successfully"));
    }

    @PatchMapping("/path-qa/dicom-store")
    public Mono<Map<String, Object>> updatePathQaDicomStore(@RequestParam Map<String, String> queryParams, @RequestBody Map<String, Object> config) {
        return configurationService.updatePathQaDicomStore(queryParams, config);
    }

}
