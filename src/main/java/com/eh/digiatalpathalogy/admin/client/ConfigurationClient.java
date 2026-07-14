package com.eh.digiatalpathalogy.admin.client;

import com.eh.digiatalpathalogy.admin.model.AppConfiguration;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.util.HttpRequestHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RefreshScope
public class ConfigurationClient {


    @Value("${spring.config.uri}")
    private String configBaseUrl;

    private final HttpRequestHandler requestHandler;

    public ConfigurationClient(HttpRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public Mono<Map<String, Object>> loadConfiguration(String application, String profile) {

        String url = Stream.of(application, profile)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("/", configBaseUrl + "/", ""));
        return requestHandler.request(url, HttpMethod.GET, null, null, null, new ParameterizedTypeReference<Map<String, Object>>() {
        });
    }

    public Mono<Map<String, Object>> updateConfig(String application, Map<String, String> queryParams, ConfigPayload request) {

        if (request.getSource().equals("common")) {
            application = null;
        }
        return requestHandler.request(buildUpdateConfigUrl(request.getSource(), application), HttpMethod.PATCH, queryParams, request.getConfig(), null, new ParameterizedTypeReference<Map<String, Object>>() {
        });
    }

    public Mono<AppConfiguration> loadConfig(String application, String profile) {

        String url = Stream.of(application, profile)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("/", configBaseUrl + "/", ""));
        return requestHandler.request(url, HttpMethod.GET, null, null, null, new ParameterizedTypeReference<AppConfiguration>() {
        });
    }

    public String buildUpdateConfigUrl(String source, String application) {
        String sourcePath = "vault".equalsIgnoreCase(source) ? "/vault/kv" : "/native";
        return (application == null || application.isEmpty())
                ? configBaseUrl + sourcePath
                : configBaseUrl + sourcePath + "/" + application;
    }

    public Mono<Void> busRefresh() {
        return requestHandler.request(configBaseUrl + "/actuator/busrefresh", HttpMethod.POST, null, null, null, new ParameterizedTypeReference<Void>() {
        });
    }

}
