package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.config.SlideScanProgressConfig;
import com.eh.digiatalpathalogy.admin.entity.DicomInstance;
import com.eh.digiatalpathalogy.admin.entity.SlideScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.ScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.model.scanstatus.TimeoutDecision;
import com.eh.digiatalpathalogy.admin.repository.DicomInstanceRepository;
import com.eh.digiatalpathalogy.admin.repository.SlideScanStatusRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.SCAN_FAILED;
import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.TIMEOUT_WARNING_COMPLETED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlideScanTimeoutMonitorService Tests")
class SlideScanTimeoutMonitorServiceTest {

    @Mock
    private RedisEntityStore redisEntityStore;
    @Mock
    private SlideScanStatusService slideScanStatusService;
    @Mock
    private SlideScanStatusRepository repository;
    @Mock
    private ConfigStore configStore;
    @Mock
    private SlideScanProgressConfig progressConfig;
    @Mock
    private SlideScanProgressConfig.Timeout timeout;
    @Mock
    private SlideScanProgressConfig.Check check;
    @Mock
    private SlideScanProgressConfig.Lock lock;
    @Mock
    private SlideScanProgressConfig.Processing processing;
    @Mock
    private DicomInstanceRepository dicomInstanceRepository;
    @InjectMocks
    private SlideScanTimeoutMonitorService service;


    private SlideScanProgressEvent createEvent() {

        LinkedHashSet<ScanStatus> events = new LinkedHashSet<>(
                Arrays.asList(
                        new ScanStatus("scan-event", "enrichment-completed", ""),
                        new ScanStatus("scan-event", "synapse-completed", "")
                )
        );

        return new SlideScanProgressEvent("ACC", "BARCODE", "SERIES", "DEVICE", "STATUS",
                "SERVICE", 50.0, events, null, null);
    }

    @Test
    @DisplayName("Should skip monitoring when timeout monitor is disabled")
    void shouldSkipWhenMonitorDisabled() {

        when(progressConfig.getTimeout()).thenReturn(timeout);
        when(timeout.isEnabled()).thenReturn(false);

        service.monitorTimeoutScans();

        verifyNoInteractions(redisEntityStore);
    }


    @Test
    @DisplayName("Should skip execution when lock is not acquired")
    void shouldSkipWhenLockNotAcquired() {

        mockTimeoutConfig();
        when(progressConfig.getTimeout()).thenReturn(timeout);
        when(redisEntityStore.acquireLock(any(), any(), any()))
                .thenReturn(Mono.just(false));
        service.monitorTimeoutScans();
        Awaitility.await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(redisEntityStore).acquireLock(any(), any(), any()));
    }

    @Test
    @DisplayName("Should handle lock acquisition errors gracefully")
    void shouldHandleAcquireLockError() {

        mockTimeoutConfig();
        when(redisEntityStore.acquireLock(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("Lock error")));
        service.monitorTimeoutScans();
        Awaitility.await().atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> verify(redisEntityStore).acquireLock(any(), any(), any()));
    }


    @Test
    @DisplayName("Should process expired barcodes successfully")
    void shouldProcessExpiredBarcodes() {

        mockTimeoutConfig();

        assertNotNull(progressConfig.getTimeout());
        assertTrue(progressConfig.getTimeout().isEnabled());

        SlideScanStatus entity = new SlideScanStatus();
        entity.setSlideBarcode("BARCODE");
        entity.setSeriesId("SERIES");
        entity.setAccessionNumber("ACC");
        entity.setDeviceSerialNumber("DEVICE");

        SlideScanProgressEvent event = createEvent();

        when(redisEntityStore.acquireLock(any(), any(), any()))
                .thenReturn(Mono.just(true));

        when(redisEntityStore.getExpired(anyString(), anyLong()))
                .thenReturn(Flux.just("BARCODE"));

        when(redisEntityStore.findByKey(anyString(), eq(SlideScanProgressEvent.class)))
                .thenReturn(Mono.just(event));

        when(repository.findBySlideBarcode(anyString()))
                .thenReturn(Mono.just(entity));

        when(dicomInstanceRepository.updateMany(any(Query.class), any(Update.class), eq(DicomInstance.class)))
                .thenReturn(Mono.just(1L));

        when(slideScanStatusService.streamSlideScanStatus(any()))
                .thenReturn(Mono.empty());

        when(configStore.getSynapseEnabled())
                .thenReturn(false);

        when(redisEntityStore.releaseLock(anyString(), anyString()))
                .thenReturn(Mono.just(true));


        service.monitorTimeoutScans();

        verify(redisEntityStore, timeout(2000))
                .acquireLock(any(), any(), any());

        verify(redisEntityStore, timeout(2000))
                .getExpired(anyString(), anyLong());

        verify(redisEntityStore, timeout(2000))
                .findByKey(anyString(), eq(SlideScanProgressEvent.class));

        verify(dicomInstanceRepository, timeout(2000))
                .updateMany(any(Query.class), any(Update.class), eq(DicomInstance.class));

        verify(slideScanStatusService, timeout(2000))
                .streamSlideScanStatus(any());
    }

    @Test
    @DisplayName("Should release lock even when processing fails")
    void shouldReleaseLockWhenProcessingFails() {

        mockTimeoutConfig();

        when(progressConfig.getTimeout()).thenReturn(timeout);
        when(redisEntityStore.acquireLock(any(), any(), any()))
                .thenReturn(Mono.just(true));
        when(redisEntityStore.getExpired(anyString(), anyLong()))
                .thenReturn(Flux.error(new RuntimeException("Boom")));
        when(redisEntityStore.releaseLock(any(), any()))
                .thenReturn(Mono.just(true));

        service.monitorTimeoutScans();
        Awaitility.await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> verify(redisEntityStore,times(2)).releaseLock(any(), any()));
    }

    @Test
    @DisplayName("Should generate redis key from barcode")
    void shouldGenerateRedisKey() {

        String result = ReflectionTestUtils.invokeMethod(service, "redisKey", "BARCODE");
        assertNotNull(result);
        assertTrue(result.contains("BARCODE"));
    }

    @Test
    @DisplayName("Should convert SlideScanStatus entity to event")
    void shouldConvertEntityToEvent() {

        SlideScanStatus status = new SlideScanStatus();
        status.setAccessionNumber("ACC");
        status.setSlideBarcode("BARCODE");
        status.setSeriesId("SERIES");
        status.setDeviceSerialNumber("DEVICE");
        status.setScanStatus("STATUS");

        SlideScanProgressEvent event = ReflectionTestUtils.invokeMethod(service, "toEvent", status);

        assertNotNull(event);
        assertEquals("BARCODE", event.slideBarcode());
        assertEquals("ACC", event.accessionNumber());
    }

    @Test
    @DisplayName("Should fetch slide status from database and cache it")
    void shouldFetchFromDbAndCache() {

        SlideScanStatus entity = new SlideScanStatus();
        entity.setSlideBarcode("BARCODE");

        when(repository.findBySlideBarcode("BARCODE"))
                .thenReturn(Mono.just(entity));
        when(redisEntityStore.save(anyString(), any()))
                .thenReturn(Mono.empty());

        Mono<SlideScanProgressEvent> result = ReflectionTestUtils.invokeMethod(service, "fetchFromDbAndCache", "BARCODE", "KEY");
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return failed decision when no events exist")
    void shouldReturnFailedWhenNoEvents() {

        SlideScanProgressEvent event = new SlideScanProgressEvent("ACC", "BAR", "SERIES", "DEVICE", "",
                "", 0.0, new LinkedHashSet<>(), null, null);
        TimeoutDecision decision = ReflectionTestUtils.invokeMethod(service, "decide", event);
        assertEquals(SCAN_FAILED, decision.status());
    }

    @Test
    @DisplayName("Should return failed decision when enrichment is incomplete")
    void shouldReturnFailedWhenEnrichmentNotCompleted() {

        LinkedHashSet<ScanStatus> events = new LinkedHashSet<>(Arrays.asList(new ScanStatus("scan-event", "enrichment-in-progress", "")));
        SlideScanProgressEvent event = new SlideScanProgressEvent("ACC", "BAR", "SER", "DEV", "",
                "", 0.0, events, null, null);
        TimeoutDecision decision = ReflectionTestUtils.invokeMethod(service, "decide", event);

        assertEquals(SCAN_FAILED, decision.status());
    }

    @Test
    @DisplayName("Should return timeout warning when synapse workflow is missing")
    void shouldReturnWarningWhenSynapseMissing() {

        when(configStore.getSynapseEnabled()).thenReturn(true);
        LinkedHashSet<ScanStatus> events = new LinkedHashSet<>(Arrays.asList(new ScanStatus("scan-event", "enrichment-completed", "")));
        SlideScanProgressEvent event = new SlideScanProgressEvent("ACC", "BAR", "SER", "DEV", "",
                "", 0.0, events, null, null);
        TimeoutDecision decision = ReflectionTestUtils.invokeMethod(service, "decide", event);

        assertEquals(TIMEOUT_WARNING_COMPLETED, decision.status());
    }

    @Test
    @DisplayName("Should return warning completed when workflow completed")
    void shouldReturnWarningCompleted() {

        when(configStore.getSynapseEnabled()).thenReturn(false);
        TimeoutDecision decision = ReflectionTestUtils.invokeMethod(service, "decide", createEvent());
        assertEquals(TIMEOUT_WARNING_COMPLETED, decision.status());
    }

    @Test
    @DisplayName("Should update dicom instance successfully")
    void shouldUpdateDicomInstance() {

        when(dicomInstanceRepository.updateMany(any(Query.class), any(Update.class), eq(DicomInstance.class)))
                .thenReturn(Mono.just(1L));
        Mono<Boolean> mono = ReflectionTestUtils.invokeMethod(service, "updateDicomInstances", createEvent(), "timeout");
        StepVerifier.create(mono)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return false when dicom instance is not found")
    void shouldReturnFalseWhenDicomNotFound() {

        when(dicomInstanceRepository.updateMany(any(Query.class), any(Update.class), eq(DicomInstance.class)))
                .thenReturn(Mono.empty());
        Mono<Boolean> mono = ReflectionTestUtils.invokeMethod(service, "updateDicomInstances", createEvent(), "timeout");
        StepVerifier.create(mono)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should propagate error when dicom update fails")
    void shouldHandleUpdateDicomError() {

        when(dicomInstanceRepository.updateMany(any(Query.class), any(Update.class), eq(DicomInstance.class)))
                .thenReturn(Mono.error(new RuntimeException("db")));
        Mono<Boolean> mono = ReflectionTestUtils.invokeMethod(service, "updateDicomInstances", createEvent(), "timeout");
        StepVerifier.create(mono)
                .expectError(RuntimeException.class)
                .verify();
    }

    private void mockTimeoutConfig() {

        when(progressConfig.getTimeout()).thenReturn(timeout);
        when(timeout.isEnabled()).thenReturn(true);
        when(timeout.getLock()).thenReturn(lock);
        when(timeout.getProcessing()).thenReturn(processing);
        when(timeout.getMinutes()).thenReturn(2L);
        when(lock.getTtlMinutes()).thenReturn(10L);
        when(processing.getParallelism()).thenReturn(2);
    }
}