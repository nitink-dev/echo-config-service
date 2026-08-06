package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.event.KafkaEventDispatcher;
import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;

import java.time.Duration;

/**
 * Reactive Kafka listener that consumes events, ensures ordered processing per key,
 * delegates handling to KafkaEventDispatcher, and commits offsets after processing.
 */
@Service
public class KafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventListener.class);

    private static final int RAILS = 32;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaReceiver<String, String> receiver;
    private final KafkaEventDispatcher kafkaEventDispatcher;

    public KafkaEventListener(KafkaReceiver<String, String> receiver, KafkaEventDispatcher kafkaEventDispatcher) {
        this.receiver = receiver;
        this.kafkaEventDispatcher = kafkaEventDispatcher;
    }

    /**
     * Starts the reactive Kafka consumption pipeline on application startup.
     */
    @PostConstruct
    public void start() {

        log.info("Starting KafkaEventListener | rails={} timeoutSeconds={}", RAILS, PROCESSING_TIMEOUT.getSeconds());

        receiver.receive()
                .map(KafkaEnvelope::from)
                .groupBy(env -> stripe(env.barcodeKey()), RAILS)
                .flatMap(groupedFlux -> groupedFlux.concatMap(this::processAndCommit), RAILS)
                .doOnSubscribe(s -> log.info("Kafka consumption stream subscribed successfully"))
                .doOnError(ex -> log.error("Kafka consumption stream failed – restarting", ex))
                .retry()
                .subscribe();
    }

    private Mono<Void> processAndCommit(KafkaEnvelope env) {

        log.debug("Received Kafka event | topic={} partition={} offset={} key={} stripe={}", env.topic(), env.partition(), env.offset(), env.key(), stripe(env.barcodeKey()));
        return kafkaEventDispatcher.dispatch(env)
                .timeout(PROCESSING_TIMEOUT)
                .then(commit(env))
                .onErrorResume(ex -> kafkaEventDispatcher.publishToDlt(env, ex)
                        .then(commit(env))
                        .doOnSuccess(v -> log.info("Event sent to DLT and committed | topic={} offset={}", env.topic(), env.offset())));
    }

    private Mono<Void> commit(KafkaEnvelope env) {
        return Mono.fromRunnable(() -> {
            env.offsetHandle().acknowledge();
            log.debug("Kafka offset committed | topic={} partition={} offset={}", env.topic(), env.partition(), env.offset());
        });
    }

    /**
     * Computes a deterministic processing stripe to ensure ordered handling per key.
     */
    private int stripe(String key) {
        return key == null ? 0 : Math.floorMod(key.hashCode(), RAILS);
    }
}