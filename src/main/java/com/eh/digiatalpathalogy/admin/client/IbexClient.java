package com.eh.digiatalpathalogy.admin.client;

import com.eh.digiatalpathalogy.admin.model.ibex.IbexCaseResponse;
import com.eh.digiatalpathalogy.admin.util.HttpRequestHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RefreshScope
public class IbexClient {

    @Value("${ibex.base-url}")
    private String ibexBaseUrl;

    @Value("${ibex.api-key}")
    private String ibexApiKey;

    private final HttpRequestHandler requestHandler;

    public IbexClient(HttpRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public Mono<IbexCaseResponse> getCase(String caseId) {
        Map<String, String> headers = Map.of("X-API-KEY", ibexApiKey);
        return requestHandler.request(
                ibexBaseUrl + "/api/v1/cases/" + caseId, HttpMethod.GET, null, null, headers,
                new ParameterizedTypeReference<IbexCaseResponse>() {}
        );
    }
}
