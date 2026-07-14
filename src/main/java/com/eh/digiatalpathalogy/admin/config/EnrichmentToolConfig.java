package com.eh.digiatalpathalogy.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "tools")
public class EnrichmentToolConfig {

    private Map<String, AppMapping> applications;

    public Map<String, AppMapping> getApplications() {
        return applications;
    }

    public void setApplications(Map<String, AppMapping> applications) {
        this.applications = applications;
    }

    public static class AppMapping {

        private Map<String, Map<String, String>> mappings;

        public Map<String, Map<String, String>> getMappings() {
            return mappings;
        }

        public void setMappings(Map<String, Map<String, String>> mappings) {
            this.mappings = mappings;
        }
    }
}
