package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.config.FormLabelsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;



@RestController
public class FormLabelsController {

    private final FormLabelsProperties formLabelsProperties;
    private static final Logger log = LoggerFactory.getLogger(FormLabelsController.class);

    public FormLabelsController(FormLabelsProperties formLabelsProperties) {
        this.formLabelsProperties = formLabelsProperties;
    }

    @GetMapping("/api/config/form-labels/{formKey}")
    public Map<String, String> getFormLabels(@PathVariable("formKey") String formKey) {
        Map<String, String> labels =
                formLabelsProperties.getForms().getOrDefault(formKey, Map.of());
        log.info("Printing form-labels: FormKey={}, Labels={}", formKey, labels);
        return labels;
    }
}