package com.eh.digiatalpathalogy.admin.config;

import com.eh.digiatalpathalogy.admin.model.HostInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.*;

@ConfigurationProperties(prefix = "health")
public class HealthTargetsProperties {
    private Set<HostInfo> microservices = new LinkedHashSet<>();
    private List<Target> thirdParties = new ArrayList<>();

    private Duration connectionTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public List<Target> getThirdParties() {
        return thirdParties;
    }

    public void setThirdParties(List<Target> thirdParties) {
        this.thirdParties = thirdParties;
    }

    public Set<HostInfo> getMicroservices() {
        return microservices;
    }

    public void setMicroservices(Set<HostInfo> microservices) {
        this.microservices = microservices;
    }

    public static class Target {
        private String name;
        private String url;
        private Map<String, String> headers = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
    }
}
