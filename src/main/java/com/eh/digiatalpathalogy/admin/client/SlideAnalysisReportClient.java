package com.eh.digiatalpathalogy.admin.client;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisRequest;
import com.eh.digiatalpathalogy.admin.util.HttpRequestHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.eh.digiatalpathalogy.admin.constant.ServiceUrls.PATH_QA_ANALYSIS;

@Component
@RefreshScope
public class SlideAnalysisReportClient {

    @Value("${path.qa-baseurl}")
    private String pathQABaseUrl;

    private final HttpRequestHandler requestHandler;

    public SlideAnalysisReportClient(HttpRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public Mono<SlideAnalysisReport> submitAnalysis(SlideAnalysisRequest request) {
        return requestHandler.request(
                pathQABaseUrl + PATH_QA_ANALYSIS + "/analyse", HttpMethod.POST, null, request, null,
                new ParameterizedTypeReference<SlideAnalysisReport>() {}
        );
    }

    public Mono<SlideAnalysisReport> analysisDetails(String analysisId) {
        Map<String, String> queryParams = Map.of("analysisId", analysisId);
        return requestHandler.request(
                pathQABaseUrl + PATH_QA_ANALYSIS + "/analysisDetails", HttpMethod.GET, queryParams, null, null,
                new ParameterizedTypeReference<SlideAnalysisReport>() {}
        );
    }
}
