package com.eh.digiatalpathalogy.admin.event;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles PathQA slide analysis events by deserializing the incoming message
 * and delegating processing to the SlideAnalysisReportService.
 */

public class PathQaSlideAnalysisEventHandler implements TopicHandler {

    private static final Logger log = LoggerFactory.getLogger(PathQaSlideAnalysisEventHandler.class);

    private final String topic;
    private final ObjectMapper objectMapper;
    private final SlideAnalysisReportService slideAnalysisReportService;

    public PathQaSlideAnalysisEventHandler(String topic, ObjectMapper objectMapper, SlideAnalysisReportService slideAnalysisReportService) {
        this.topic = topic;
        this.objectMapper = objectMapper;
        this.slideAnalysisReportService = slideAnalysisReportService;
        log.info("QaSlideAnalysisEventHandler initialized | topic={}", topic);
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public Mono<Void> handle(KafkaEnvelope env) {

        return Mono.fromCallable(() -> objectMapper.readValue(env.payload(), SlideAnalysisMessage.class))
                .doOnNext(msg -> log.debug("QA slide analysis event received | topic={} offset={} key={}", env.topic(), env.offset(), env.key()))
                .flatMap(slideAnalysisReportService::processAnalysisRequest)
                .doOnSuccess(v -> log.info("QA slide analysis processed successfully | offset={} key={}", env.offset(), env.key()))
                .onErrorMap(ex -> {
                    log.error("QA slide analysis processing failed | topic={} offset={} key={}", env.topic(), env.offset(), env.key(), ex);
                    return new RuntimeException("Failed to process QA slide analysis event", ex);
                })
                .then();
    }
}
