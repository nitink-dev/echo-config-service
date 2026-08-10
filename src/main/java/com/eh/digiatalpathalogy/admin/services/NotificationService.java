package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.model.EntityChangeNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.email}")
    private String emailTopic;

    public NotificationService(KafkaSender<String, String> sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    public <T> Mono<Void> notifyEntityChange(String entityType, T oldData, T newData) {

        final String TEMPLATE_KEY = "ENTITY_CHANGE_DEFAULT";

        EntityChangeNotification<T> notification = new EntityChangeNotification<>(TEMPLATE_KEY, entityType, oldData, newData);

        return Mono.fromCallable(() -> {
            String payload = objectMapper.writeValueAsString(notification);
            return payload;
        }).flatMap(payload -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(emailTopic, TEMPLATE_KEY, payload);

            return sender.send(Mono.just(SenderRecord.create(record, null))).next();
        }).doOnNext(result -> {

            if (result != null && result.recordMetadata() != null) {

                log.info("Kafka publish successful. entityType='{}', topic='{}', partition={}, offset={}", entityType, result.recordMetadata().topic(), result.recordMetadata().partition(), result.recordMetadata().offset());

            } else {

                log.warn("Kafka publish completed but metadata is null for entityType='{}'", entityType);
            }
        }).doOnSuccess(result -> log.info("Notification workflow completed successfully for entityType='{}'", entityType)).doOnError(error -> log.error("Notification workflow failed. entityType='{}', topic='{}', error='{}'", entityType, emailTopic, error.getMessage(), error)).onErrorResume(error -> {

            log.error("Suppressed notification failure for entityType='{}'. Application flow will continue.", entityType, error);

            return Mono.empty();
        }).then();
    }
}