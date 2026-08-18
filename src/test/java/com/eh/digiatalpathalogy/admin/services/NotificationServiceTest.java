package com.eh.digiatalpathalogy.admin.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private KafkaSender<String, String> sender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() throws Exception {
        notificationService = new NotificationService(sender, new ObjectMapper());
        Field topicField = NotificationService.class.getDeclaredField("emailTopic");
        topicField.setAccessible(true);
        topicField.set(notificationService, "email-svc-topic");
    }

    @SuppressWarnings("unchecked")
    private String captureTemplateKey(Mono<Void> notification) {
        when(sender.send(any())).thenReturn(Flux.just(Mockito.mock(SenderResult.class)));

        StepVerifier.create(notification).verifyComplete();

        ArgumentCaptor<Mono<SenderRecord<String, String, Object>>> captor = ArgumentCaptor.forClass(Mono.class);
        verify(sender).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue().block();
        return record.key();
    }

    @Test
    @DisplayName("create (oldData=null, newData present) -> ENTITY_CREATE_DEFAULT")
    void create_usesCreateTemplateKey() {
        String key = captureTemplateKey(
                notificationService.notifyEntityChange("scanner", null, Map.of("name", "SH_TEST_scanner_14AUG")));

        assertEquals("ENTITY_CREATE_DEFAULT", key);
    }

    @Test
    @DisplayName("update (oldData and newData present) -> ENTITY_CHANGE_DEFAULT")
    void update_usesChangeTemplateKey() {
        String key = captureTemplateKey(
                notificationService.notifyEntityChange("scanner",
                        Map.of("name", "old-name"), Map.of("name", "new-name")));

        assertEquals("ENTITY_CHANGE_DEFAULT", key);
    }

    @Test
    @DisplayName("delete (newData=null) -> ENTITY_DELETE_DEFAULT")
    void delete_usesDeleteTemplateKey() {
        String key = captureTemplateKey(
                notificationService.notifyEntityChange("scanner", Map.of("name", "old-name"), null));

        assertEquals("ENTITY_DELETE_DEFAULT", key);
    }
}