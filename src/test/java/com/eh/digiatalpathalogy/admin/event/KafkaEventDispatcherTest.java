package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.testdata.KafkaEnvelopeTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventDispatcherTest {

    @Mock
    private TopicHandler handler;
    @Mock
    private KafkaSender<String, String> sender;

    private KafkaEventDispatcher dispatcher;
    private KafkaEnvelope env;

    @BeforeEach
    void setup() {
        env = KafkaEnvelopeTestData.sampleScanEvent();
        when(handler.topic()).thenReturn("scan-topic");
        dispatcher = new KafkaEventDispatcher(List.of(handler), sender);
    }

    @Test
    void shouldDispatchSuccessfully() {
        when(handler.handle(env)).thenReturn(Mono.empty());
        StepVerifier.create(dispatcher.dispatch(env)).verifyComplete();
        verify(handler).handle(env);
    }

    @Test
    void shouldThrowIfNoHandler() {
        KafkaEnvelope unknown = new KafkaEnvelope("unknown", "k", 0, 1L, "{}", "k", null);

        StepVerifier.create(dispatcher.dispatch(unknown))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void shouldSendToDlt() {
        when(sender.send(any())).thenReturn(Flux.just(Mockito.mock(SenderResult.class)));
        StepVerifier.create(dispatcher.publishToDlt(env, new RuntimeException("error")))
                .verifyComplete();
        verify(sender).send(any());
    }
}