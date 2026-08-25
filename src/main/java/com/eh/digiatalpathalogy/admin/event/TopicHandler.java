package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import reactor.core.publisher.Mono;


/**
 * Contract for handling Kafka events for a specific topic.
 * Implementations must be reactive, non-blocking, and idempotent.
 */
public interface TopicHandler<T> {


    /**
     * @return the Kafka topic name this handler processes
     */
    String topic();


    /**
     * Processes the given Kafka event.
     *
     * @param kafkaEnvelope Kafka message wrapper containing payload and metadata
     * @return Mono that completes when event processing finishes
     */
    Mono<Void> handle(KafkaEnvelope<T> kafkaEnvelope);
}