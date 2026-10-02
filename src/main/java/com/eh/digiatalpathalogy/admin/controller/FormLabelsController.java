package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.config.FormLabelsProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FormLabelsController {

    private final FormLabelsProperties formLabelsProperties;

    public FormLabelsController(FormLabelsProperties formLabelsProperties) {
        this.formLabelsProperties = formLabelsProperties;
    }

    @GetMapping("/api/config/form-labels/{formKey}")
    public Map<String, String> getFormLabels(@PathVariable("formKey") String formKey) {
        return formLabelsProperties.getForms().getOrDefault(formKey, Map.of());
    }
}
