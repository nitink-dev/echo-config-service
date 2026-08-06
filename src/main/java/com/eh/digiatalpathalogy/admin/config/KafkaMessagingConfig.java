package com.eh.digiatalpathalogy.admin.config;


import com.eh.digiatalpathalogy.admin.event.PathQaSlideAnalysisEventHandler;
import com.eh.digiatalpathalogy.admin.event.SlideScanProgressEventHandler;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagingConfig.class);

    @Value("${kafka.topic.qa-slide}")
    private String qaSlideTopic;
    @Value("${kafka.topic.scan-progress}")
    private String scanProgressTopic;
    @Value("${spring.kafka.consumer.group-id:admin-consumer-group}")
    private String consumerGroupId;

    private final KafkaProperties kafkaProperties;

    public KafkaMessagingConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @PostConstruct
    public void logKafkaConfig() {
        log.info("Kafka Bootstrap Server: {}", kafkaProperties.getBootstrapServers());
    }

    @Bean
    public ReceiverOptions<String, String> receiverOptions() {

        Map<String, Object> configProps = kafkaProperties.buildConsumerProperties();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);

        return ReceiverOptions.<String, String>create(configProps)
                .subscription(List.of(qaSlideTopic, scanProgressTopic))
                .commitInterval(Duration.ofSeconds(1))
                .commitBatchSize(200);
    }

    @Bean
    public KafkaReceiver<String, String> kafkaReceiver(ReceiverOptions<String, String> receiverOptions) {
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public SenderOptions<String, String> senderOptions() {
        return SenderOptions.create(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaSender<String, String> kafkaSender(SenderOptions<String, String> senderOptions) {
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public PathQaSlideAnalysisEventHandler qaSlideHandler(ObjectMapper objectMapper, SlideAnalysisReportService service) {
        return new PathQaSlideAnalysisEventHandler(qaSlideTopic, objectMapper, service);
    }

    @Bean
    public SlideScanProgressEventHandler slideScanProgressHandler(ObjectMapper objectMapper, SlideScanStatusService slideScanStatusService) {
        return new SlideScanProgressEventHandler(scanProgressTopic, objectMapper, slideScanStatusService);
    }

}
