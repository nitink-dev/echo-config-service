package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles slide scan progress events by deserializing the Kafka payload
 * and updating scan status via the SlideScanStatusService.
 */
public class SlideScanProgressEventHandler implements TopicHandler< SlideScanProgressEvent > {

    private static final Logger log = LoggerFactory.getLogger( SlideScanProgressEventHandler.class );

    private final String topic;
    private final SlideScanStatusService slideScanStatusService;

    public SlideScanProgressEventHandler ( String topic, SlideScanStatusService slideScanStatusService ) {
        this.topic = topic;
        this.slideScanStatusService = slideScanStatusService;
        log.info( "SlideScanProgressEventHandler initialized | topic = {}", topic );
    }

    @Override
    public String topic ( ) {
        return topic;
    }

    @Override
    public Mono< Void > handle ( KafkaEnvelope< SlideScanProgressEvent > env ) {

        return Mono.just( env.payload( ) ).doOnNext( event -> log.debug( "Slide scan progress event received | topic={} partition={} offset={} key={}", env.topic( ), env.partition( ), env.offset( ), env.key( ) ) ).flatMap( slideScanStatusService::streamSlideScanStatus )
                .doOnSuccess( v -> log.debug( "Slide scan progress processed successfully | offset={} key={}", env.offset( ), env.key( ) ) ).onErrorMap( ex -> {
                    log.error( "Slide scan progress processing failed | topic={} offset={} key={}", env.topic( ), env.offset( ), env.key( ), ex );
                    return new RuntimeException( "Failed to process slide scan progress event", ex );
                } );
    }

}
