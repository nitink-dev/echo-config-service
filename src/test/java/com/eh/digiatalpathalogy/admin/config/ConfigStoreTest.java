package com.eh.digiatalpathalogy.admin.config;

import com.eh.digiatalpathalogy.admin.client.ConfigurationClient;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.AppConfiguration;
import com.eh.digiatalpathalogy.admin.model.ConfigPropertySource;
import com.eh.digiatalpathalogy.admin.model.HostInfo;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SERVICE_HOST_INFO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigStoreTest {

    @Mock
    private ConfigurationClient configurationClient;
    @Mock
    private RedisEntityStore redisStore;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EnrichmentToolConfig toolConfig;
    @Mock
    private SlideScanProgressConfig slideScanProgressConfig;

    @InjectMocks
    private ConfigStore configStore;

    @Test
    @DisplayName("getAllPropertiesForApplication: when app mapping missing -> ResourceNotFoundException")
    void getAllPropertiesForApplication_whenNoMapping_thenError() throws Exception {
        given(toolConfig.getApplications()).willReturn(Collections.emptyMap());

        StepVerifier.create(configStore.getAllPropertiesForApplication("appA"))
                .expectError(ResourceNotFoundException.class)
                .verify();

        then(redisStore).shouldHaveNoInteractions();
        then(configurationClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getAllPropertiesForApplication: Redis cache hit -> returns cached values, no config service call")
    void getAllPropertiesForApplication_whenCacheHit_thenReturnCached() throws Exception {
        String app = "appA";

        EnrichmentToolConfig.AppMapping mapping = new EnrichmentToolConfig.AppMapping();
        mapping.setMappings(Map.of("category1", Map.of("prop1", "some.path")));

        given(toolConfig.getApplications()).willReturn(Map.of(app, mapping));
        given(redisStore.fetchApplicationConfig(app)).willReturn(Mono.just(Map.of("prop1", "cachedValue")));

        StepVerifier.create(configStore.getAllPropertiesForApplication(app))
                .assertNext(map -> assertThat(map).containsEntry("prop1", "cachedValue"))
                .verifyComplete();

        then(configurationClient).should(never()).loadConfig(anyString());
    }

    @Test
    @DisplayName("getAllPropertiesForApplication: Redis cache miss -> fetch from config service and save to Redis")
    void getAllPropertiesForApplication_whenCacheMiss_thenFetchAndSave() throws Exception {
        String app = "appA";

        EnrichmentToolConfig.AppMapping mapping = new EnrichmentToolConfig.AppMapping();
        mapping.setMappings(Map.of("category1", Map.of(
                "prop1", "a.b.c",
                "missingProp", "x.y.z"
        )));

        given(toolConfig.getApplications()).willReturn(Map.of(app, mapping));
        given(redisStore.fetchApplicationConfig(app)).willReturn(Mono.just(Collections.emptyMap()));

        AppConfiguration appConfig = mock(AppConfiguration.class);
        ConfigPropertySource cps = mock(ConfigPropertySource.class);

        Map<String, Object> src = new HashMap<>();
        src.put("a.b.c", "valueFromConfig");

        given(appConfig.getPropertySources()).willReturn(List.of(cps));
        given(cps.getSource()).willReturn(src);
        given(configurationClient.loadConfig(eq(app))).willReturn(Mono.just(appConfig));
        given(redisStore.findByKey(SERVICE_HOST_INFO + app, HostInfo.class))
                .willReturn(Mono.just(new HostInfo("10.1.1.1", 8080, app, "Test Service")));

        given(redisStore.save(anyString(), any())).willReturn(Mono.empty());

        StepVerifier.create(configStore.getAllPropertiesForApplication(app))
                .assertNext(map -> {
                    assertThat(map).containsEntry("prop1", "valueFromConfig");
                    assertThat(map).containsEntry("missingProp", "test");
                })
                .verifyComplete();

        then(redisStore).should().save(eq("config:appA"), any(Map.class));
    }

    @Test
    @DisplayName("refreshEnrichmentConfig: aggregates config for multiple applications (cache hits)")
    void refreshEnrichmentConfig_shouldAggregateMultipleApps() throws Exception {

        EnrichmentToolConfig.AppMapping mappingA = appMapping(Map.of("cat", Map.of("p1", "k1")));
        EnrichmentToolConfig.AppMapping mappingB = appMapping(Map.of("cat", Map.of("p2", "k2")));

        given(toolConfig.getApplications()).willReturn(Map.of(
                "appA", mappingA,
                "appB", mappingB
        ));

        given(redisStore.fetchApplicationConfig("appA")).willReturn(Mono.just(Map.of("p1", "v1")));
        given(redisStore.fetchApplicationConfig("appB")).willReturn(Mono.just(Map.of("p2", "v2")));

        StepVerifier.create(configStore.refreshEnrichmentConfig())
                .assertNext(all -> {
                    assertThat(all).hasSize(2);
                    assertThat(all.get("appA")).containsEntry("p1", "v1");
                    assertThat(all.get("appB")).containsEntry("p2", "v2");
                })
                .verifyComplete();

        then(configurationClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("refreshEnrichmentConfig: when no mappings configured -> returns empty map")
    void refreshEnrichmentConfig_whenNoMappings_thenEmpty() throws Exception {

        given(toolConfig.getApplications()).willReturn(null);
        StepVerifier.create(configStore.refreshEnrichmentConfig())
                .expectNext(Collections.emptyMap())
                .verifyComplete();
    }

    @Test
    @DisplayName("deleteAllConfigKeys: deletes pattern config:* and completes")
    void deleteAllConfigKeys_shouldDeletePattern() {

        given(redisStore.deleteKeysByPattern("config:*")).willReturn(Mono.empty());
        StepVerifier.create(configStore.deleteAllConfigKeys()).verifyComplete();
        then(redisStore).should().deleteKeysByPattern("config:*");
    }

    @Test
    @DisplayName("deleteAllConfigKeys: when redis delete fails -> error propagates")
    void deleteAllConfigKeys_whenDeleteFails_thenError() {

        given(redisStore.deleteKeysByPattern("config:*")).willReturn(Mono.error(new RuntimeException("redis down")));
        StepVerifier.create(configStore.deleteAllConfigKeys())
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("toTyped: returns empty when raw is null")
    void toTyped_whenRawNull_thenEmpty() {
        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) ReflectionTestUtils.invokeMethod(
                configStore, "toTyped", null, String.class
        );
        StepVerifier.create(mono).verifyComplete();
    }

    @Test
    @DisplayName("toTyped: returns same value when type matches")
    void toTyped_whenAlreadyCorrectType_thenReturnValue() {
        @SuppressWarnings("unchecked")
        Mono<String> mono = (Mono<String>) ReflectionTestUtils.invokeMethod(configStore, "toTyped", "abc", String.class);
        StepVerifier.create(mono)
                .expectNext("abc")
                .verifyComplete();
    }

    @Test
    @DisplayName("toTyped: uses ObjectMapper when type differs")
    void toTyped_whenDifferentType_thenConvert() {

        given(objectMapper.convertValue(any(), eq(Integer.class))).willReturn(10);
        @SuppressWarnings("unchecked")
        Mono<Integer> mono = (Mono<Integer>) ReflectionTestUtils.invokeMethod(configStore, "toTyped", "10", Integer.class);
        StepVerifier.create(mono)
                .expectNext(10)
                .verifyComplete();
    }

    @Test
    @DisplayName("toTyped: propagates IllegalArgumentException from ObjectMapper")
    void toTyped_whenConvertFails_thenError() {

        given(objectMapper.convertValue(any(), eq(Integer.class))).willThrow(new IllegalArgumentException("bad"));

        @SuppressWarnings("unchecked")
        Mono<Integer> mono = (Mono<Integer>) ReflectionTestUtils.invokeMethod(configStore, "toTyped", "x", Integer.class);

        StepVerifier.create(mono)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("writeAllToRedis: empty map -> completes without saving")
    void writeAllToRedis_whenEmpty_thenComplete() {
        @SuppressWarnings("unchecked")
        Mono<Void> mono = (Mono<Void>) ReflectionTestUtils.invokeMethod(
                configStore, "writeAllToRedis", Collections.emptyMap()
        );

        StepVerifier.create(mono).verifyComplete();
        then(redisStore).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("writeAllToRedis: filters null values and saves remaining")
    void writeAllToRedis_filtersNulls() {
        given(redisStore.save(anyString(), any())).willReturn(Mono.empty());

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "v1");
        input.put("k2", null);

        @SuppressWarnings("unchecked")
        Mono<Void> mono = (Mono<Void>) ReflectionTestUtils.invokeMethod(configStore, "writeAllToRedis", input);

        StepVerifier.create(mono).verifyComplete();
        then(redisStore).should(times(1)).save(eq("k1"), eq("v1"));
        then(redisStore).should(never()).save(eq("k2"), any());
    }

    @Test
    @DisplayName("extractAllMappedValues: no propertySources -> returns empty map")
    void extractAllMappedValues_whenNoPropertySources_thenEmpty() {
        Map<String, Object> config = new HashMap<>();

        @SuppressWarnings("unchecked")
        Mono<Map<String, Object>> mono = (Mono<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                configStore, "extractAllMappedValues", "appA", config
        );

        StepVerifier.create(mono)
                .expectNext(Collections.emptyMap())
                .verifyComplete();
    }

    @Test
    @DisplayName("initOnStartup: delegates to refreshAll()")
    void initOnStartup_callsRefreshAll() {

        ConfigStore spyStore = spy(new ConfigStore(configurationClient, redisStore, objectMapper, toolConfig, slideScanProgressConfig));
        willDoNothing().given(spyStore).refreshAll();
        spyStore.initOnStartup();
        then(spyStore).should(times(1)).refreshAll();
    }

    private EnrichmentToolConfig.AppMapping appMapping(Map<String, Map<String, String>> nested) {
        EnrichmentToolConfig.AppMapping mapping = new EnrichmentToolConfig.AppMapping();
        mapping.setMappings(nested);
        return mapping;
    }

    @Test
    @DisplayName("get: unknown redisKey -> IllegalArgumentException")
    void get_whenUnknownRedisKey_thenIllegalArgument() {
        StepVerifier.create(configStore.get("UNKNOWN_KEY", String.class, "appA", "p1"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("flatMapAppMapping: flattens nested mappings")
    void flatMapAppMapping_shouldFlatten() {
        EnrichmentToolConfig.AppMapping mapping = new EnrichmentToolConfig.AppMapping();
        mapping.setMappings(Map.of(
                "cat1", Map.of("p1", "x.y"),
                "cat2", Map.of("p2", "a.b")
        ));

        Map<String, String> flat = ConfigStore.flatMapAppMapping(mapping);
        assertThat(flat).containsEntry("p1", "x.y");
        assertThat(flat).containsEntry("p2", "a.b");
    }

    @Test
    @DisplayName("refreshAll(): happy path – deletes keys, refreshes ALL, then refreshes enrichment config")
    void refreshAll_happyPath() {

        ConfigStore spyStore = spy(new ConfigStore(configurationClient, redisStore, objectMapper, toolConfig, slideScanProgressConfig));

        given(redisStore.deleteKeysByPattern("config:*")).willReturn(Mono.empty());
        given(configurationClient.loadConfiguration(anyString()))
                .willReturn(Mono.just(Map.of("propertySources", Collections.emptyList())));

        doReturn(Mono.just(Collections.emptyMap())).when(spyStore).refreshEnrichmentConfig();

        spyStore.refreshAll();
        then(redisStore).should(timeout(200)).deleteKeysByPattern("config:*");
        then(configurationClient).should(timeout(200).times(1)).loadConfiguration(anyString());
        then(spyStore).should(timeout(200).times(1)).refreshEnrichmentConfig();


        @SuppressWarnings("unchecked")
        Map<String, ?> inFlight =
                (Map<String, ?>) ReflectionTestUtils.getField(spyStore, "inFlightRefresh");
        assertThat(inFlight).isNotNull().isInstanceOf(ConcurrentHashMap.class);
        assertThat(inFlight).isEmpty();
    }
}