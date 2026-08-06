package com.eh.digiatalpathalogy.admin.config;

import com.eh.digiatalpathalogy.admin.model.scanstatus.SseEventMessage;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.*;

/**
 * Stream component that broadcasts slide scan status updates to subscribers.
 * Uses Reactor Sinks for real‑time fan‑out and includes periodic heartbeat events.
 */
@Component
public class SlideScanStatusStream {

    private static final Logger log = LoggerFactory.getLogger(SlideScanStatusStream.class);

    /**
     * Sink used to publish slide scan status updates to multiple subscribers.
     */
    private final Sinks.Many<SseEventMessage<?>> sink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * Shared Flux that streams status updates with buffering and backpressure handling.
     */
    private final Flux<SseEventMessage<?>> flux = sink.asFlux()
            .onBackpressureBuffer(
                    5000,   // buffer limit
                    dropped -> log.warn("Dropped event due to backpressure: {}", dropped),
                    BufferOverflowStrategy.DROP_OLDEST)
            .publish()
            .autoConnect();

    /**
     * Publishes a new slide status update into the stream.
     */
    public void publish(SlideScanStatusDto status) {
        sink.tryEmitNext(SseEventMessage.of(SLIDE_STATUS, status.slideBarcode(), status));
    }

    /**
     * Returns the live stream of active slide scan events.
     */
    public Flux<SseEventMessage<?>> streamActiveSlides() {
        return flux;
    }

    /**
     * Heartbeat Flux that emits a lightweight SSE ping every 10 seconds.
     */
    private final Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(Duration.ofSeconds(10))
            .map(tick -> ServerSentEvent.builder()
                    .event("message")
                    .data(SseEventMessage.signal(HEARTBEAT))
                    .build()
            )
            .doOnNext(evt -> log.debug("SSE heartbeat emitted: {}", evt.event()));

    /**
     * Provides a heartbeat SSE stream to keep client connections alive.
     */
    public Flux<ServerSentEvent<Object>> getHeartbeat() {
        return heartbeat;
    }
    
    public void publishResearchSignal() {
        sink.tryEmitNext(SseEventMessage.signal(RESEARCH_EVENT));
    }

}
