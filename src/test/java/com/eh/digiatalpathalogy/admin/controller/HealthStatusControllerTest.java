package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.config.HealthTargetsProperties;
import com.eh.digiatalpathalogy.admin.model.HealthStatusResult;
import com.eh.digiatalpathalogy.admin.model.HostInfo;
import com.eh.digiatalpathalogy.admin.services.HealthStatusService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@WebFluxControllerTest(controllers = HealthStatusController.class)
class HealthStatusControllerTest {







































    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private HealthTargetsProperties props;

    @MockitoBean
    private HealthStatusService healthStatusService;

    @Test
    @DisplayName("GET /api/health/status → 200 returns sample response structure")
    void getHealthStatus_success_returnsSampleResponseStructure() {

        when(healthStatusService.resolveCoreDependencies()).thenReturn(Mono.just(Map.of("kafka", "UP", "mongodb", "UP", "redis", "UNKNOWN")));

        HostInfo dbHostInfo = new HostInfo("10.1001.10.1",8080,"eh-database-connector","Database Service");
        HostInfo receiverHostInfo = new HostInfo("10.1001.10.2",8081,"eh-dicom-receiver","Dicom Receiver Service");
        Set<HostInfo> microServices = Set.of(dbHostInfo, receiverHostInfo);

        when(props.getMicroservices()).thenReturn(microServices);
        when(healthStatusService.checkHttpEndpoints(microServices)).thenReturn(
                Mono.just(List.of(
                        new HealthStatusResult("UP", "http://10.1001.10.1:8080/actuator/health", "Database Service", 200, 10, null),
                        new HealthStatusResult("UP", "http://10.1001.10.2:8081/actuator/health", "Dicom Receiver Service", 200, 11, null)
                ))
        );

        HealthTargetsProperties.Target clinysis = new HealthTargetsProperties.Target();
        clinysis.setName("Clinysis");
        clinysis.setUrl("111.11.111.11");
        clinysis.setHeaders(Map.of());

        HealthTargetsProperties.Target ibex = new HealthTargetsProperties.Target();
        ibex.setName("IBEX");
        ibex.setUrl("22.222.222.22");
        ibex.setHeaders(Map.of());

        HealthTargetsProperties.Target synapse = new HealthTargetsProperties.Target();
        synapse.setName("Synapse");
        synapse.setUrl("33.333.333.33");
        synapse.setHeaders(Map.of());

        when(props.getThirdParties()).thenReturn(List.of(clinysis, ibex, synapse));

        HealthStatusResult clinysisDown = new HealthStatusResult().down("Clinysis", "111.11.111.11", null, 3003, "Timeout");
        HealthStatusResult ibexDown = new HealthStatusResult().down("IBEX", "22.222.222.22", null, 3002, "Timeout");
        HealthStatusResult synapseUp = new HealthStatusResult("UP", "33.333.333.33", "Synapse", 0, 6, null);

        when(healthStatusService.checkIcmpEndpoints(anyList()))
                .thenReturn(Mono.just(List.of(clinysisDown, ibexDown, synapseUp)));

        webTestClient.get()
                .uri("/api/health/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("application/json")
                .expectBody()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.dependencies.kafka").isEqualTo("UP")
                .jsonPath("$.dependencies.mongodb").isEqualTo("UP")
                .jsonPath("$.dependencies.redis").isEqualTo("UNKNOWN")
                .jsonPath("$.microservices.length()").isEqualTo(2)
                .jsonPath("$.microservices[0].name").isEqualTo("Database Service")
                .jsonPath("$.microservices[0].url")
                .isEqualTo("http://10.1001.10.1:8080/actuator/health")
                .jsonPath("$.microservices[0].status").isEqualTo("UP")
                .jsonPath("$.microservices[0].httpStatus").isEqualTo(200)
                .jsonPath("$.microservices[0].latencyMs").isEqualTo(10)
                .jsonPath("$.microservices[0].error").value(Assertions::assertNull)
                .jsonPath("$.microservices[1].name")
                .isEqualTo("Dicom Receiver Service")
                .jsonPath("$.microservices[1].url")

                .isEqualTo("http://10.1001.10.2:8081/actuator/health")
                .jsonPath("$.microservices[1].status").isEqualTo("UP")
                .jsonPath("$.microservices[1].httpStatus").isEqualTo(200)
                .jsonPath("$.microservices[1].latencyMs").isEqualTo(11)
                .jsonPath("$.microservices[1].error").value(Assertions::assertNull)
                .jsonPath("$.thirdParties.length()").isEqualTo(3)
                .jsonPath("$.thirdParties[0].name").isEqualTo("Clinysis")
                .jsonPath("$.thirdParties[0].url").isEqualTo("111.11.111.11")
                .jsonPath("$.thirdParties[0].status").isEqualTo("DOWN")
                .jsonPath("$.thirdParties[0].httpStatus")
                .value(Assertions::assertNull)
                .jsonPath("$.thirdParties[0].latencyMs").isEqualTo(3003)
                .jsonPath("$.thirdParties[0].error").isEqualTo("Timeout")
                .jsonPath("$.thirdParties[1].name").isEqualTo("IBEX")
                .jsonPath("$.thirdParties[1].url").isEqualTo("22.222.222.22")
                .jsonPath("$.thirdParties[1].status").isEqualTo("DOWN")
                .jsonPath("$.thirdParties[1].httpStatus")
                .value(Assertions::assertNull)
                .jsonPath("$.thirdParties[1].latencyMs").isEqualTo(3002)
                .jsonPath("$.thirdParties[1].error").isEqualTo("Timeout")
                .jsonPath("$.thirdParties[2].name").isEqualTo("Synapse")
                .jsonPath("$.thirdParties[2].url").isEqualTo("33.333.333.33")
                .jsonPath("$.thirdParties[2].status").isEqualTo("UP")
                .jsonPath("$.thirdParties[2].httpStatus").isEqualTo(0)
                .jsonPath("$.thirdParties[2].latencyMs").isEqualTo(6)
                .jsonPath("$.thirdParties[2].error").value(Assertions::assertNull);

        verify(healthStatusService).resolveCoreDependencies();
        verify(healthStatusService).checkHttpEndpoints(microServices);
        verify(healthStatusService).checkIcmpEndpoints(anyList());
        verifyNoMoreInteractions(healthStatusService);
    }

    @Test
    @DisplayName("GET /api/health/status → 5xx when HealthEndpoint.health throws exception")
    void getHealthStatus_failed_whenHealthEndpointThrows_returns5xx() {

        when(healthStatusService.resolveCoreDependencies())
                .thenReturn(Mono.error(new RuntimeException("actuator down")));

        webTestClient.get()
                .uri("/api/health/status")
                .exchange()
                .expectStatus().is5xxServerError();

        verify(healthStatusService, times(1)).resolveCoreDependencies();
    }

    @Test
    @DisplayName("GET /api/health/status → 5xx when pingAllHttp throws exception")
    void getHealthStatus_failed_whenPingAllHttpThrows_returns5xx() {

        HostInfo dbHostInfo = new HostInfo("10.1001.10.1",8080,"eh-database-connector","Database Service");
        HostInfo receiverHostInfo = new HostInfo("10.1001.10.2",8081,"eh-dicom-receiver","Dicom Receiver Service");
        Set<HostInfo> microServices = Set.of(dbHostInfo, receiverHostInfo);

        when(healthStatusService.resolveCoreDependencies()).thenReturn(Mono.just(Map.of("kafka", "UP", "mongodb", "UP", "redis", "UNKNOWN")));
        when(props.getMicroservices()).thenReturn(microServices);
        when(props.getThirdParties()).thenReturn(List.of(
                target("Clinysis", "111.11.111.11"),
                target("IBEX", "22.222.222.22"),
                target("Synapse", "33.333.333.33")
        ));

        when(healthStatusService.checkHttpEndpoints(microServices)).thenThrow(new RuntimeException("microservices ping failed"));
        when(healthStatusService.checkIcmpEndpoints(anyList())).thenReturn(Mono.just(List.of(
                new HealthStatusResult().down("Clinysis", "111.11.111.11", null, 3003, "Timeout"),
                new HealthStatusResult().down("IBEX", "22.222.222.22", null, 3002, "Timeout"),
                new HealthStatusResult("UP", "33.333.333.33", "Synapse", 0, 6, null)
        )));

        webTestClient.get()
                .uri("/api/health/status")
                .exchange()
                .expectStatus().is5xxServerError();

        verify(healthStatusService).resolveCoreDependencies();
        verify(healthStatusService).checkHttpEndpoints(microServices);
    }

    @Test
    @DisplayName("GET /api/health/status → 5xx when pingAllIcmp throws exception")
    void getHealthStatus_failed_whenPingAllIcmpThrows_returns5xx() {

        HostInfo dbHostInfo = new HostInfo("10.1001.10.1",8080,"eh-database-connector","Database Service");
        HostInfo receiverHostInfo = new HostInfo("10.1001.10.2",8081,"eh-dicom-receiver","Dicom Receiver Service");
        Set<HostInfo> microServices = Set.of(dbHostInfo, receiverHostInfo);

        when(healthStatusService.resolveCoreDependencies()).thenReturn(Mono.just(Map.of("kafka", "UP", "mongodb", "UP", "redis", "UNKNOWN")));
        when(props.getMicroservices()).thenReturn(microServices);
        when(props.getThirdParties()).thenReturn(List.of(
                target("Clinysis", "111.11.111.11"),
                target("IBEX", "22.222.222.22"),







                target("Synapse", "33.333.333.33")
        ));
        when(healthStatusService.checkHttpEndpoints(microServices)).thenReturn(Mono.just(List.of(
                new HealthStatusResult("UP", "http://10.1001.10.1:8080/actuator/health", "Database Service", 200, 10, null),
                new HealthStatusResult("UP", "http://10.1001.10.2:8081/actuator/health", "Dicom Receiver Service", 200, 11, null)
        )));
        when(healthStatusService.checkIcmpEndpoints(anyList())).thenReturn(Mono.error(new RuntimeException("icmp failed")));

        webTestClient.get()
                .uri("/api/health/status")
                .exchange()
                .expectStatus().is5xxServerError();

        verify(healthStatusService).resolveCoreDependencies();
        verify(healthStatusService).checkIcmpEndpoints(anyList());
    }

    private static HealthTargetsProperties.Target target(String name, String url) {
        HealthTargetsProperties.Target t = new HealthTargetsProperties.Target();
        t.setName(name);
        t.setUrl(url);
        t.setHeaders(Map.of());
        return t;
    }
}

















































































































































































































































































































































































































































































































