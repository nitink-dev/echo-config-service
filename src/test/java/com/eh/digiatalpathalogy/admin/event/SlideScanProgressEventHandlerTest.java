package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import com.eh.digiatalpathalogy.admin.testdata.KafkaEnvelopeTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlideScanProgressEventHandlerTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SlideScanStatusService service;
    private SlideScanProgressEventHandler handler;
    private KafkaEnvelope env;

    @BeforeEach
    void setup() {
        handler = new SlideScanProgressEventHandler("scan-topic", objectMapper, service);
        env = KafkaEnvelopeTestData.sampleScanEvent();
    }

    @Test
    void shouldProcessSuccessfully() throws Exception {

        SlideScanProgressEvent event = new SlideScanProgressEvent("ACC-001", "BARCODE123", "SERIES-001", "DEVICE-001", "IN_PROGRESS", "scanner-service", 55.5, null);

        when(objectMapper.readValue(anyString(), eq(SlideScanProgressEvent.class))).thenReturn(event);
        when(service.streamSlideScanStatus(event)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(env)).verifyComplete();
        verify(service).streamSlideScanStatus(event);
    }

    @Test
    void shouldFailOnJsonError() throws Exception {
        when(objectMapper.readValue(anyString(), eq(SlideScanProgressEvent.class))).thenThrow(new RuntimeException("invalid"));
        StepVerifier.create(handler.handle(env))
                .expectError(RuntimeException.class)
                .verify();
    }
}
