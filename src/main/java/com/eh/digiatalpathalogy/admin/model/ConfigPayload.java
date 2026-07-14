package com.eh.digiatalpathalogy.admin.model;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class ConfigPayload {

    @NotNull(message = "Source must not be null")
    private String source;
    @NotNull(message = "Config must not be null")
    private Map<String, Object> config;

    public ConfigPayload() {
    }

    public ConfigPayload(String source, Map<String, Object> config) {
        this.source = source;
        this.config = config;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
