package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
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
class PathQaSlideAnalysisEventHandlerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SlideAnalysisReportService service;

    private PathQaSlideAnalysisEventHandler handler;

    private KafkaEnvelope env;

    @BeforeEach
    void setup() {
        handler = new PathQaSlideAnalysisEventHandler("qa-topic", objectMapper, service);
        env = KafkaEnvelopeTestData.sampleQaEvent();
    }

    @Test
    void shouldProcessSuccessfully() throws Exception {

        SlideAnalysisMessage msg = new SlideAnalysisMessage("BARCODE123", "1.2.3", "1.2.3.4", "DEVICE-001", "store1");

        when(objectMapper.readValue(anyString(), eq(SlideAnalysisMessage.class))).thenReturn(msg);
        when(service.processAnalysisRequest(msg)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(env))
                .verifyComplete();
        verify(service).processAnalysisRequest(msg);
    }

    @Test
    void shouldFailOnServiceError() throws Exception {
        SlideAnalysisMessage msg = new SlideAnalysisMessage("BARCODE123", "1", "2", "DEVICE", "store");

        when(objectMapper.readValue(anyString(), eq(SlideAnalysisMessage.class))).thenReturn(msg);
        when(service.processAnalysisRequest(msg)).thenReturn(Mono.error(new RuntimeException("fail")));

        StepVerifier.create(handler.handle(env))
                .expectError(RuntimeException.class)
                .verify();
    }
}