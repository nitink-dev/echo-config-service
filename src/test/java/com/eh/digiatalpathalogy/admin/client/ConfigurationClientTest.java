package com.eh.digiatalpathalogy.admin.client;

import com.eh.digiatalpathalogy.admin.model.AppConfiguration;
import com.eh.digiatalpathalogy.admin.model.ConfigPayload;
import com.eh.digiatalpathalogy.admin.model.ConfigPropertySource;
import com.eh.digiatalpathalogy.admin.util.HttpRequestHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.CONFIG_SOURCE_GIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationClientTest {

    @Mock
    private HttpRequestHandler requestHandler;
    @InjectMocks
    private ConfigurationClient client;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(client, "configBaseUrl", "http://config-server");
        ReflectionTestUtils.setField(client, "activeProfile", "dev");
    }

    private String urlIs(String expected) {
        return argThat(actual -> Objects.equals(expected, actual));
    }

    private HttpMethod methodIs(HttpMethod expected) {
        return argThat(actual -> actual == expected);
    }

    private Map<String, String> mapIs(Map<String, String> expected) {
        return argThat(actual -> Objects.equals(expected, actual));
    }

    private Object bodyIs(Object expected) {
        return argThat(actual -> Objects.equals(expected, actual));
    }

    @SuppressWarnings("unchecked")
    private <T> void stubRequest(String url, HttpMethod method, Map<String, String> queryParams, Object requestBody, Map<String, String> headers, Mono<T> result) {

        when(requestHandler.request(urlIs(url), methodIs(method), queryParams == null ? isNull() : mapIs(queryParams), requestBody == null ? isNull() : bodyIs(requestBody), headers == null ? isNull() : mapIs(headers),
                any(ParameterizedTypeReference.class)
        )).thenReturn(result);
    }

    private void verifyRequest(String url, HttpMethod method, Map<String, String> queryParams, Object requestBody, Map<String, String> headers) {

        verify(requestHandler, times(1)).request(urlIs(url), methodIs(method), queryParams == null ? isNull() : mapIs(queryParams), requestBody == null ? isNull() : bodyIs(requestBody),
                headers == null ? isNull() : mapIs(headers),
                any(ParameterizedTypeReference.class)
        );
    }

    static Stream<Object[]> loadConfigurationCases() {
        return Stream.of(
                new Object[]{"eh-admin-console", "dev", "http://config-server/eh-admin-console/dev"},
                new Object[]{"eh-admin-console", null, "http://config-server/eh-admin-console"},
                new Object[]{"", "dev", "http://config-server/dev"},
                new Object[]{"", "", "http://config-server/"},
                new Object[]{null, "dev", "http://config-server/dev"},
                new Object[]{"eh-admin-console", "", "http://config-server/eh-admin-console"}
        );
    }

    @ParameterizedTest(name = "loadConfiguration(app={0}, profile={1}) → {2}")
    @MethodSource("loadConfigurationCases")
    @DisplayName("loadConfiguration: builds URL correctly and delegates to requestHandler")
    void loadConfiguration_buildsUrlCorrectly(String application, String profile, String expectedUrl) {

        ReflectionTestUtils.setField(client, "activeProfile", profile);
        Map<String, Object> expected = Map.of("value", "test");
        stubRequest(expectedUrl, HttpMethod.GET, null, null, null, Mono.just(expected));

        StepVerifier.create(client.loadConfiguration(application))
                .expectNext(expected)
                .verifyComplete();

        verifyRequest(expectedUrl, HttpMethod.GET, null, null, null);
        verifyNoMoreInteractions(requestHandler);
    }

    @Test
    @DisplayName("loadConfig: app + profile → GET {base}/{app}/{profile} returning AppConfiguration")
    void loadConfig_appAndProfile() {
        AppConfiguration expected = buildAppConfiguration();
        String expectedUrl = "http://config-server/eh-admin-console/dev";

        stubRequest(expectedUrl, HttpMethod.GET, null, null, null, Mono.just(expected));

        StepVerifier.create(client.loadConfig("eh-admin-console"))
                .assertNext(actual -> {

                    assertThat(actual.getName()).isEqualTo("eh-admin-console");
                    assertThat(actual.getProfiles()).containsExactly("default");
                    assertThat(actual.getLabel()).isNull();
                    assertThat(actual.getVersion()).isNull();
                    assertThat(actual.getState()).isNull();

                    assertThat(actual.getPropertySources()).hasSize(2);

                    ConfigPropertySource ps1 = actual.getPropertySources().get(0);
                    assertThat(ps1.getSource()).containsEntry("path.retry-attempt", 5);
                    assertThat(ps1.getSource()).containsEntry("path.duration", 1);

                    ConfigPropertySource ps2 = actual.getPropertySources().get(1);
                    assertThat(ps2.getSource()).containsEntry("message.config.sending-app", "EHDPIS");
                    assertThat(ps2.getSource()).containsEntry("message.config.sending-facility", "");
                })
                .verifyComplete();

        verifyRequest(expectedUrl, HttpMethod.GET, null, null, null);
        verifyNoMoreInteractions(requestHandler);
    }

    @Test
    @DisplayName("buildUpdateConfigUrl: git + app → {base}/git/{app}")
    void buildUpdateConfigUrl_native_withApp() {
        assertEquals("http://config-server/git/eh-dicom-receiver",
                client.buildUpdateConfigUrl("git", "eh-dicom-receiver"));
    }

    @Test
    @DisplayName("buildUpdateConfigUrl: git + null app → {base}/git")
    void buildUpdateConfigUrl_native_noApp() {
        assertEquals("http://config-server/git",
                client.buildUpdateConfigUrl(CONFIG_SOURCE_GIT, null));
    }

    @Test
    @DisplayName("buildUpdateConfigUrl: vault + app → {base}/vault/kv/{app}")
    void buildUpdateConfigUrl_vault_withApp() {
        assertEquals("http://config-server/vault/kv/eh-admin-console",
                client.buildUpdateConfigUrl("vault", "eh-admin-console"));
    }

    @Test
    @DisplayName("buildUpdateConfigUrl: vault + null app → {base}/vault/kv")
    void buildUpdateConfigUrl_vault_noApp() {
        assertEquals("http://config-server/vault/kv",
                client.buildUpdateConfigUrl("vault", null));
    }

    @Test
    @DisplayName("updateConfig: non-common source → PATCH {base}/git/{app} with queryParams and body")
    void updateConfig_native_appProvided() {
        ReflectionTestUtils.setField(client, "activeProfile", "dev");
        ConfigPayload payload = mock(ConfigPayload.class);
        when(payload.getSource()).thenReturn(CONFIG_SOURCE_GIT);
        when(payload.getConfig()).thenReturn(Map.of("a", 1));

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("profile", "dev");
        Map<String, Object> expectedResponse = Map.of("ok", true);

        String expectedUrl = "http://config-server/git/eh-dicom-receiver";
        Object expectedBody = Map.of("a", 1);

        stubRequest(expectedUrl, HttpMethod.PATCH, queryParams, expectedBody, null, Mono.just(expectedResponse));

        StepVerifier.create(client.updateConfig("eh-dicom-receiver", queryParams, payload))
                .expectNext(expectedResponse)
                .verifyComplete();

        verifyRequest(expectedUrl, HttpMethod.PATCH, queryParams, expectedBody, null);
        verifyNoMoreInteractions(requestHandler);
    }

    @Test
    @DisplayName("updateConfig: source=common → application forced null → PATCH {base}/native")
    void updateConfig_common_forcesNullApplication() {
        ReflectionTestUtils.setField(client, "activeProfile", "dev");
        ConfigPayload payload = mock(ConfigPayload.class);
        when(payload.getSource()).thenReturn("common");     // forces application=null
        when(payload.getConfig()).thenReturn(Map.of("x", "y"));

        Map<String, Object> expectedResponse = Map.of("done", 1);

        String expectedUrl = "http://config-server/git";
        Object expectedBody = Map.of("x", "y");

        Map<String, String> expectedQueryParams = Map.of("profile", "dev");

        stubRequest(expectedUrl, HttpMethod.PATCH, expectedQueryParams, expectedBody, null, Mono.just(expectedResponse));

        StepVerifier.create(client.updateConfig("ignored-app", null, payload))
                .expectNext(expectedResponse)
                .verifyComplete();

        verifyRequest(expectedUrl, HttpMethod.PATCH, expectedQueryParams, expectedBody, null);
        verifyNoMoreInteractions(requestHandler);
    }

    @Test
    @DisplayName("busRefresh: POST {base}/actuator/busrefresh")
    void busRefresh_postsToActuator() {
        String expectedUrl = "http://config-server/actuator/busrefresh";

        stubRequest(expectedUrl, HttpMethod.POST, null, null, null, Mono.empty());

        StepVerifier.create(client.busRefresh())
                .verifyComplete();

        verifyRequest(expectedUrl, HttpMethod.POST, null, null, null);
        verifyNoMoreInteractions(requestHandler);
    }

    private AppConfiguration buildAppConfiguration() {

        ConfigPropertySource srcAppProperties = new ConfigPropertySource();
        srcAppProperties.setName("file:/configs/eh-admin-console/application.yml");
        Map<String, Object> src1 = new HashMap<>();
        src1.put("path.retry-attempt", 5);
        src1.put("path.duration", 1);
        srcAppProperties.setSource(src1);

        ConfigPropertySource commonSource = new ConfigPropertySource();
        commonSource.setName("file:/configs/common/application.yml");
        Map<String, Object> src2 = new HashMap<>();
        src2.put("message.config.sending-app", "EHDPIS");
        src2.put("message.config.sending-facility", "");
        commonSource.setSource(src2);

        AppConfiguration appConfiguration = new AppConfiguration();
        appConfiguration.setName("eh-admin-console");
        appConfiguration.setProfiles(List.of("default"));
        appConfiguration.setLabel(null);
        appConfiguration.setVersion(null);
        appConfiguration.setState(null);
        appConfiguration.setPropertySources(List.of(srcAppProperties, commonSource));

        return appConfiguration;
    }
}
