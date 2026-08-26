package com.eh.digiatalpathalogy.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka.topic")
@RefreshScope
public class KafkaTopicConfig {
    private String email;
    private String pathqa;
    private String scanProgress;

    public String getEmail ( ) {
        return email;
    }

    public void setEmail ( String email ) {
        this.email = email;
    }

    public String getPathqa ( ) {
        return pathqa;
    }

    public void setPathqa ( String pathqa ) {
        this.pathqa = pathqa;
    }

    public String getScanProgress ( ) {
        return scanProgress;
    }

    public void setScanProgress ( String scanProgress ) {
        this.scanProgress = scanProgress;
    }
}
