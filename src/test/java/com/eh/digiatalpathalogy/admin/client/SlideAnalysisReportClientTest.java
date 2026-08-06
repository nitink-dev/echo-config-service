package com.eh.digiatalpathalogy.admin.client;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisRequest;
import com.eh.digiatalpathalogy.admin.util.HttpRequestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Objects;

import static com.eh.digiatalpathalogy.admin.constant.ServiceUrls.PATH_QA_ANALYSIS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlideAnalysisReportClientTest {

    @Mock
    private HttpRequestHandler requestHandler;
    @InjectMocks
    private SlideAnalysisReportClient client;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(client, "pathQABaseUrl", "http://pathqa-server");
    }

    private String urlIs(String expected) {
        return argThat(actual -> Objects.equals(expected, actual));
    }

    private HttpMethod methodIs(HttpMethod expected) {
        return argThat(actual -> actual == expected);
    }

    @SuppressWarnings("unchecked")
    private <T> void stub(String expectedUrl, HttpMethod expectedMethod, Map<String, String> expectedQueryParams, Object expectedBody,
                          Mono<T> result) {

        when(requestHandler.request(urlIs(expectedUrl), methodIs(expectedMethod), expectedQueryParams == null ? isNull() : argThat(q -> Objects.equals(expectedQueryParams, q)),
                expectedBody == null ? isNull() : argThat(b -> Objects.equals(expectedBody, b)), isNull(), any(ParameterizedTypeReference.class)
        )).thenReturn(result);
    }

    private void verifyOnce(String expectedUrl, HttpMethod expectedMethod, Map<String, String> expectedQueryParams,
                            Object expectedBody) {

        verify(requestHandler, times(1)).request(urlIs(expectedUrl), methodIs(expectedMethod),
                expectedQueryParams == null ? isNull() : argThat(q -> Objects.equals(expectedQueryParams, q)),
                expectedBody == null ? isNull() : argThat(b -> Objects.equals(expectedBody, b)), isNull(), any(ParameterizedTypeReference.class)
        );
        verifyNoMoreInteractions(requestHandler);
    }

    @Test
    @DisplayName("submitAnalysis: POST /analyse → returns SlideAnalysisReport")
    void submitAnalysis_success() {

        SlideAnalysisRequest request = mock(SlideAnalysisRequest.class);
        SlideAnalysisReport expected = mock(SlideAnalysisReport.class);

        String expectedUrl = "http://pathqa-server" + PATH_QA_ANALYSIS + "/analyse";
        stub(expectedUrl, HttpMethod.POST, null, request, Mono.just(expected));

        StepVerifier.create(client.submitAnalysis(request))
                .expectNext(expected)
                .verifyComplete();

        verifyOnce(expectedUrl, HttpMethod.POST, null, request);
    }

    @Test
    @DisplayName("analysisDetails: GET /analysisDetails?analysisId=... → returns SlideAnalysisReport")
    void analysisDetails_success() {

        SlideAnalysisReport expected = mock(SlideAnalysisReport.class);

        String analysisId = "A-123";
        Map<String, String> expectedQuery = Map.of("analysisId", analysisId);
        String expectedUrl = "http://pathqa-server" + PATH_QA_ANALYSIS + "/analysisDetails";

        stub(expectedUrl, HttpMethod.GET, expectedQuery, null, Mono.just(expected));

        StepVerifier.create(client.analysisDetails(analysisId))
                .expectNext(expected)
                .verifyComplete();

        verifyOnce(expectedUrl, HttpMethod.GET, expectedQuery, null);
    }

    @Test
    @DisplayName("submitAnalysis: when handler errors → error is propagated")
    void submitAnalysis_errorPropagates() {
        SlideAnalysisRequest request = mock(SlideAnalysisRequest.class);

        String expectedUrl = "http://pathqa-server" + PATH_QA_ANALYSIS + "/analyse";

        stub(expectedUrl, HttpMethod.POST, null, request, Mono.error(new RuntimeException("boom")));

        StepVerifier.create(client.submitAnalysis(request))
                .expectErrorMatches(ex -> ex instanceof RuntimeException && ex.getMessage().equals("boom"))
                .verify();

        verifyOnce(expectedUrl, HttpMethod.POST, null, request);
    }

    @Test
    @DisplayName("analysisDetails: when handler errors → error is propagated")
    void analysisDetails_errorPropagates() {
        String analysisId = "A-123";

        Map<String, String> expectedQuery = Map.of("analysisId", analysisId);
        String expectedUrl = "http://pathqa-server" + PATH_QA_ANALYSIS + "/analysisDetails";

        stub(expectedUrl, HttpMethod.GET, expectedQuery, null, Mono.error(new RuntimeException("down")));

        StepVerifier.create(client.analysisDetails(analysisId))
                .expectErrorMatches(ex -> ex instanceof RuntimeException && ex.getMessage().equals("down"))
                .verify();

        verifyOnce(expectedUrl, HttpMethod.GET, expectedQuery, null);
    }
}