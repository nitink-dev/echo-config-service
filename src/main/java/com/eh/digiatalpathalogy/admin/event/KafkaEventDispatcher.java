package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central dispatcher for routing Kafka events to their respective handlers
 * with Dead Letter Topic (DLT) support for failures.
 */
@Component
public class KafkaEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventDispatcher.class);

    private final Map<String, TopicHandler> handlers;
    private final KafkaSender<String, String> sender;
    private final String dltTopic = "dead-letter-topic";

    public KafkaEventDispatcher(List<TopicHandler> handlerList, KafkaSender<String, String> sender) {
        this.handlers = handlerList.stream().collect(Collectors.toMap(TopicHandler::topic, Function.identity()));
        this.sender = sender;
        log.info("KafkaEventDispatcher initialized with handlers={}, dltTopic={}", handlers.keySet(), dltTopic);
    }

    public Mono<Void> dispatch(KafkaEnvelope env) {

        log.debug("Dispatching Kafka event | topic={} partition={} offset={} key={}", env.topic(), env.partition(), env.offset(), env.key());
        TopicHandler handler = handlers.get(env.topic());
        if (handler == null) {
            log.error("No TopicHandler registered | topic={} key={} offset={}", env.topic(), env.key(), env.offset());
            return Mono.error(new IllegalStateException("No handler registered for topic: " + env.topic()));
        }
        return handler.handle(env)
                .doOnSuccess(v -> log.debug("Event processed successfully | topic={} offset={}", env.topic(), env.offset()));
    }

    public Mono<Void> publishToDlt(KafkaEnvelope env, Throwable ex) {

        log.error("Publishing event to DLT | originalTopic={} partition={} offset={} key={} errorType={} errorMessage={}", env.topic(), env.partition(), env.offset(), env.key(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        ProducerRecord<String, String> dltRecord = new ProducerRecord<>(dltTopic, env.key(), env.payload());
        dltRecord.headers()
                .add("x-original-topic", bytes(env.topic()))
                .add("x-original-partition", bytes(String.valueOf(env.partition())))
                .add("x-original-offset", bytes(String.valueOf(env.offset())))
                .add("x-exception-type", bytes(ex.getClass().getName()))
                .add("x-exception-message", bytes(ex.getMessage()));

        return sender.send(Mono.just(SenderRecord.create(dltRecord, null)))
                .next()
                .doOnSuccess(r -> log.info("Event published to DLT successfully | dltTopic={} originalTopic={} offset={}", dltTopic, env.topic(), env.offset()))
                .then();
    }

    private byte[] bytes(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }
}