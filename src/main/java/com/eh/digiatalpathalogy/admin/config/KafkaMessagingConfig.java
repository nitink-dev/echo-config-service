package com.eh.digiatalpathalogy.admin.config;


import com.eh.digiatalpathalogy.admin.event.PathQaSlideAnalysisEventHandler;
import com.eh.digiatalpathalogy.admin.event.SlideScanProgressEventHandler;
import com.eh.digiatalpathalogy.admin.services.SlideAnalysisReportService;
import com.eh.digiatalpathalogy.admin.services.SlideScanStatusService;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
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

    @Value("${spring.kafka.consumer.group-id:admin-consumer-group}")
    private String consumerGroupId;

    private final KafkaTopicConfig kafkaTopicConfig;
    private final KafkaProperties kafkaProperties;

    public KafkaMessagingConfig( KafkaTopicConfig kafkaTopicConfig, KafkaProperties kafkaProperties) {
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.kafkaProperties = kafkaProperties;
    }

    @PostConstruct
    public void logKafkaConfig() {
        log.info("Kafka Bootstrap Server: {}", kafkaProperties.getBootstrapServers());
    }

    @Bean
    public ReceiverOptions<String, Object> receiverOptions() {

        Map<String, Object> configProps = kafkaProperties.buildConsumerProperties();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put( JsonDeserializer.USE_TYPE_INFO_HEADERS, true );
        configProps.put( JsonDeserializer.TRUSTED_PACKAGES, "*" );
        configProps.put( JsonDeserializer.TYPE_MAPPINGS, String.join( ",", "path-qa:com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage", "scan-progress:com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent") );

        return ReceiverOptions.<String, Object>create(configProps)
                .subscription(List.of( kafkaTopicConfig.getPathqa( ), kafkaTopicConfig.getScanProgress( )))
                .commitInterval(Duration.ofSeconds(1))
                .commitBatchSize(200);
    }

    @Bean
    public KafkaReceiver<String, Object> kafkaReceiver(ReceiverOptions<String, Object> receiverOptions) {
        return KafkaReceiver.create(receiverOptions);
    }

    @Bean
    public SenderOptions<String, Object> senderOptions() {
        Map< String, Object > configProps = kafkaProperties.buildProducerProperties( );
        configProps.put( ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class );
        configProps.put( JsonSerializer.TYPE_MAPPINGS, String.join( ",", "entity-email:com.eh.digiatalpathalogy.admin.model.EntityChangeNotification" ) );
        return SenderOptions.create(configProps);
    }

    @Bean
    public KafkaSender<String, Object> kafkaSender(SenderOptions<String, Object> senderOptions) {
        return KafkaSender.create(senderOptions);
    }

    @Bean
    public PathQaSlideAnalysisEventHandler qaSlideHandler(SlideAnalysisReportService service) {
        return new PathQaSlideAnalysisEventHandler( kafkaTopicConfig.getPathqa( ), service);
    }

    @Bean
    public SlideScanProgressEventHandler slideScanProgressHandler(SlideScanStatusService slideScanStatusService) {
        return new SlideScanProgressEventHandler( kafkaTopicConfig.getScanProgress( ),  slideScanStatusService);
    }

}
