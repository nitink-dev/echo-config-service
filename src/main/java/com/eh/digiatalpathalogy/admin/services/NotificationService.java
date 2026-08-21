package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.model.EntityChangeNotification;
import com.eh.digiatalpathalogy.admin.model.NotificationEntityType;
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

    public <T> Mono<Void> notifyEntityChange(String rawEntityType, T oldData, T newData) {

        final String entityType = NotificationEntityType.toDisplayName(rawEntityType);

        log.info("Starting notification workflow. entityType='{}'", entityType);

        final String TEMPLATE_KEY =
                newData == null ? "ENTITY_DELETE_DEFAULT"
                        : oldData == null ? "ENTITY_CREATE_DEFAULT"
                        : "ENTITY_CHANGE_DEFAULT";

        log.info(
                "Resolved template key='{}' for entityType='{}'. Operation={}",
                TEMPLATE_KEY,
                entityType,
                newData == null ? "DELETE"
                        : oldData == null ? "CREATE"
                        : "UPDATE");

        EntityChangeNotification<T> notification =
                new EntityChangeNotification<>(TEMPLATE_KEY, entityType, oldData, newData);

        log.info(
                "Created EntityChangeNotification object. entityType='{}', templateKey='{}'",
                entityType,
                TEMPLATE_KEY);

        return Mono.fromCallable(() -> {

                    log.info(
                            "Serializing notification payload. entityType='{}', templateKey='{}'",
                            entityType,
                            TEMPLATE_KEY);

                    String payload = objectMapper.writeValueAsString(notification);

                    log.info(
                            "Successfully serialized notification payload. entityType='{}', payloadLength={}",
                            entityType,
                            payload.length());

                    log.info("Serialized payload: {}", payload);

                    return payload;
                })
                .flatMap(payload -> {

                    log.info(
                            "Preparing Kafka record. entityType='{}', topic='{}', key='{}'",
                            entityType,
                            emailTopic,
                            TEMPLATE_KEY);

                    ProducerRecord<String, String> record =
                            new ProducerRecord<>(emailTopic, TEMPLATE_KEY, payload);

                    log.info(
                            "Sending notification to Kafka. entityType='{}', topic='{}'",
                            entityType,
                            emailTopic);

                    return sender.send(
                                    Mono.just(SenderRecord.create(record, null)))
                            .next();
                })
                .doOnNext(result -> {

                    if (result != null && result.recordMetadata() != null) {

                        log.info(
                                "Kafka publish successful. entityType='{}', topic='{}', partition={}, offset={}",
                                entityType,
                                result.recordMetadata().topic(),
                                result.recordMetadata().partition(),
                                result.recordMetadata().offset());

                    } else {

                        log.info(
                                "Warning-Kafka publish completed but metadata is null for entityType='{}'",
                                entityType);
                    }
                })
                .doOnSuccess(result ->
                        log.info(
                                "Notification workflow completed successfully for entityType='{}'",
                                entityType))
                .doOnError(error ->
                        log.info(
                                "Err-Notification workflow failed. entityType='{}', topic='{}', error='{}'",
                                entityType,
                                emailTopic,
                                error.getMessage(),
                                error))
                .onErrorResume(error -> {

                    log.info(
                            "Err-Suppressed notification failure for entityType='{}'. Application flow will continue.",
                            entityType,
                            error);

                    return Mono.empty();
                })
                .then();
    }
}