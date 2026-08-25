package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.testdata.KafkaEnvelopeTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathQaSlideAnalysisEventHandlerTest {

    @Mock
    private SlideAnalysisReportService service;

    private PathQaSlideAnalysisEventHandler handler;

    private KafkaEnvelope<SlideAnalysisMessage> env;

    @BeforeEach
    void setup() {
        handler = new PathQaSlideAnalysisEventHandler("qa-topic", service);
        env = KafkaEnvelopeTestData.sampleQaEvent();
    }

    @Test
    void shouldProcessSuccessfully() throws Exception {

        SlideAnalysisMessage msg = new SlideAnalysisMessage("BARCODE123", "1.2.3", "1.2.3.4", "DEVICE-001", "store1");

        when(service.processAnalysisRequest(msg)).thenReturn(Mono.empty());

        StepVerifier.create(handler.handle(env))
                .verifyComplete();
        verify(service).processAnalysisRequest(msg);
    }

    @Test
    void shouldFailOnServiceError() throws Exception {
        SlideAnalysisMessage msg = new SlideAnalysisMessage("BARCODE123", "1", "2", "DEVICE", "store");

        when(service.processAnalysisRequest(msg)).thenReturn(Mono.error(new RuntimeException("fail")));

        StepVerifier.create(handler.handle(env))
                .expectError(RuntimeException.class)
                .verify();
    }
}