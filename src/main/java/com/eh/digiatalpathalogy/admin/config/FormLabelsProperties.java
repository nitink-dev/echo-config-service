package com.eh.digiatalpathalogy.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "form-labels")
@RefreshScope
public class FormLabelsProperties {

    private Map<String, Map<String, String>> forms = new LinkedHashMap<>();

    public Map<String, Map<String, String>> getForms() {
        return forms;
    }

    public void setForms(Map<String, Map<String, String>> forms) {
        this.forms = forms;
    }
}
