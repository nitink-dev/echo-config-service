package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.HealthTargetsProperties;
import com.eh.digiatalpathalogy.admin.model.HealthStatusResult;
import com.eh.digiatalpathalogy.admin.model.HostInfo;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.ReactiveHealthContributorRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthStatusServiceTest {

    @Mock
    ReactiveHealthContributorRegistry registry;
    @Mock
    private RedisEntityStore redisStore;

    private HealthStatusService service;
    private HealthTargetsProperties props;


    @BeforeEach
    void setUp() {
        props = new HealthTargetsProperties();
        props.setConnectionTimeout(Duration.ofSeconds(2));
        props.setReadTimeout(Duration.ofSeconds(3));
        service = new HealthStatusService(props, registry, redisStore);
    }

    @Test
    @DisplayName("checkHttpEndpoints: 2xx → UP, non-2xx → DOWN")
    void checkHttpEndpoints_mapsStatusCodes() {

        HostInfo dbHostInfo = new HostInfo("10.1001.10.1", 8080, "eh-database-connector", "Database Service");
        HostInfo receiverHostInfo = new HostInfo("10.1001.10.2", 8081, "eh-dicom-receiver", "Dicom Receiver Service");
        Set<HostInfo> microServices = Set.of(dbHostInfo, receiverHostInfo);

        when(redisStore.findByKey(anyString(), eq(HostInfo.class))).thenReturn(Mono.just(receiverHostInfo));

        WebClient webClient = mockWebClient(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        ReflectionTestUtils.setField(service, "webClient", webClient);

        Mono<List<HealthStatusResult>> result = service.checkHttpEndpoints(microServices);
        StepVerifier.create(result)
                .assertNext(list -> {
                    assertEquals(2, list.size());
                    assertEquals("UP", list.get(0).getStatus());
                    assertEquals(200, list.get(0).getHttpStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("checkHttpEndpoints: exception → DOWN")
    void checkHttpEndpoints_exceptionPath() {

        HostInfo dbHostInfo = new HostInfo("10.1001.10.1", 8080, "eh-database-connector", "Database Service");
        HostInfo receiverHostInfo = new HostInfo("10.1001.10.2", 8081, "eh-dicom-receiver", "Dicom Receiver Service");
        Set<HostInfo> microServices = Set.of(dbHostInfo, receiverHostInfo);

        when(redisStore.findByKey(anyString(), eq(HostInfo.class))).thenReturn(Mono.just(receiverHostInfo));

        WebClient webClient = mockErrorWebClient(new RuntimeException("boom"));
        ReflectionTestUtils.setField(service, "webClient", webClient);
        StepVerifier.create(service.checkHttpEndpoints(microServices))
                .assertNext(list -> {
                    HealthStatusResult r = list.get(0);
                    assertEquals("DOWN", r.getStatus());
                    assertTrue(r.getError().contains("RuntimeException"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("executeBlockingIcmpPing: UP when exit=0")
    void executeBlockingIcmpPing_upPath() throws Exception {

        HealthTargetsProperties.Target t = target("Clinysis", "111.11.111.11", Map.of());

        Process process = mock(Process.class);
        doReturn(true).when(process).waitFor(anyLong(), any(TimeUnit.class));
        when(process.exitValue()).thenReturn(0);

        try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class,
                (pb, ctx) -> doReturn(process).when(pb).start())) {
            HealthStatusResult result = (HealthStatusResult) invokePrivate(service, "executeBlockingIcmpPing", new Class[]{HealthTargetsProperties.Target.class}, new Object[]{t});

            assertEquals("UP", result.getStatus());
            assertEquals("111.11.111.11", result.getUrl());
            assertEquals(0, result.getHttpStatus());
            assertNull(result.getError());

            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    @DisplayName("executeBlockingIcmpPing: timeout → DOWN + destroyForcibly")
    void executeBlockingIcmpPing_timeoutPath() throws Exception {

        HealthTargetsProperties.Target t = target("Clinysis", "111.11.111.11", Map.of());

        Process process = mock(Process.class);
        doReturn(false).when(process).waitFor(anyLong(), any(TimeUnit.class));

        try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (pb, ctx) -> doReturn(process).when(pb).start())) {
            HealthStatusResult result = (HealthStatusResult) invokePrivate(service, "executeBlockingIcmpPing", new Class[]{HealthTargetsProperties.Target.class}, new Object[]{t});

            assertEquals("DOWN", result.getStatus());
            assertEquals("Timeout", result.getError());

            verify(process).destroyForcibly();
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    @DisplayName("checkIcmpEndpoints: non-zero exit → DOWN with output")
    void checkIcmpEndpoints_nonZeroExit() throws Exception {

        HealthTargetsProperties.Target t = target("IBEX", "22.22.22.22", Map.of());
        Process process = mock(Process.class);

        try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (pb, ctx) -> when(pb.start()).thenReturn(process))) {
            StepVerifier.create(service.checkIcmpEndpoints(List.of(t)))
                    .assertNext(list -> {
                        HealthStatusResult r = list.get(0);
                        assertEquals("DOWN", r.getStatus());
                    })
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("checkIcmpEndpoints: ProcessBuilder.start throws")
    void checkIcmpEndpoints_exceptionPath() {

        HealthTargetsProperties.Target t = target("Synapse", "33.33.33.33", Map.of());
        try (MockedConstruction<ProcessBuilder> mocked = mockConstruction(ProcessBuilder.class, (pb, ctx) -> when(pb.start()).thenThrow(new IOException("no ping")))) {
            StepVerifier.create(service.checkIcmpEndpoints(List.of(t)))
                    .assertNext(list -> {
                        HealthStatusResult r = list.get(0);
                        assertEquals("DOWN", r.getStatus());
                    })
                    .verifyComplete();
        }
    }

    @Test
    @DisplayName("resolveHost: URL and raw IP")
    void resolveHost_parsing() throws Exception {

        String h1 = (String) invokePrivate(service, "resolveHost", new Class[]{String.class}, new Object[]{"http://receiver:8080/health"});
        String h2 = (String) invokePrivate(service, "resolveHost", new Class[]{String.class}, new Object[]{"8.8.8.8"});

        assertEquals("receiver", h1);
        assertEquals("8.8.8.8", h2);
    }

    private static HealthTargetsProperties.Target target(String name, String url, Map<String, String> headers) {

        HealthTargetsProperties.Target t = new HealthTargetsProperties.Target();
        t.setName(name);
        t.setUrl(url);
        t.setHeaders(headers);
        return t;
    }

    private static Object invokePrivate(Object target, String method, Class<?>[] types, Object[] args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private WebClient mockWebClient(HttpStatus... statuses) {

        WebClient webClient = mock(WebClient.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);

        AtomicInteger index = new AtomicInteger(0);
        when(uriSpec.exchangeToMono(any()))
                .thenAnswer(invocation -> {
                    int i = Math.min(index.getAndIncrement(), statuses.length - 1);
                    ClientResponse response = ClientResponse.create(statuses[i]).build();
                    @SuppressWarnings("unchecked")
                    Function<ClientResponse, Mono<?>> fn = (Function<ClientResponse, Mono<?>>) invocation.getArgument(0);
                    return fn.apply(response);
                });

        return webClient;
    }

    private WebClient mockErrorWebClient(Throwable error) {

        WebClient wc = mock(WebClient.class);
        @SuppressWarnings("rawtypes")
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        when(wc.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        when(uriSpec.exchangeToMono(any())).thenReturn(Mono.error(error));
        return wc;
    }

}
