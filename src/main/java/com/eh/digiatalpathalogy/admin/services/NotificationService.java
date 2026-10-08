package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.FormLabelsProperties;
import com.eh.digiatalpathalogy.admin.config.KafkaTopicConfig;
import com.eh.digiatalpathalogy.admin.model.EntityChangeNotification;
import com.eh.digiatalpathalogy.admin.model.NotificationEntityType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger( NotificationService.class );

    private final KafkaSender< String, Object > sender;
    private final KafkaTopicConfig kafkaTopicConfig;
    private final FormLabelsProperties formLabelsProperties;
    private final ObjectMapper objectMapper;

    public NotificationService ( KafkaSender< String, Object > sender, KafkaTopicConfig kafkaTopicConfig, FormLabelsProperties formLabelsProperties, ObjectMapper objectMapper ) {
        this.sender = sender;
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.formLabelsProperties = formLabelsProperties;
        this.objectMapper = objectMapper;
    }

    public < T > Mono< Void > notifyEntityChange ( String rawEntityType, T oldData, T newData ) {

        final String entityType = NotificationEntityType.toDisplayName( rawEntityType );
        log.info( "Starting notification workflow. entityType='{}'", entityType );

        final String TEMPLATE_KEY = newData == null ? "ENTITY_DELETE_DEFAULT" : oldData == null ? "ENTITY_CREATE_DEFAULT" : "ENTITY_CHANGE_DEFAULT";
        log.info( "Resolved template key='{}' for entityType='{}'. Operation={}", TEMPLATE_KEY, entityType, newData == null ? "DELETE" : oldData == null ? "CREATE" : "UPDATE" );

        Object normalizedOldData = normalizeKeys( rawEntityType, oldData );
        Object normalizedNewData = normalizeKeys( rawEntityType, newData );

        EntityChangeNotification< Object > notification = new EntityChangeNotification<>( TEMPLATE_KEY, entityType, normalizedOldData, normalizedNewData );
        log.info( "Created EntityChangeNotification object. entityType='{}', templateKey='{}'", entityType, TEMPLATE_KEY );

        return Mono.fromCallable( ( ) -> {
                    log.info( "Successfully serialized notification payload. entityType='{}',templateKey ={}, payloadLength={}", entityType, TEMPLATE_KEY, notification );
                    return notification;
                } ).flatMap( payload -> {

                    log.info( "Preparing Kafka record. entityType='{}', topic='{}', key='{}'", entityType, kafkaTopicConfig.getEmail(), TEMPLATE_KEY );
                    ProducerRecord< String, Object > record = new ProducerRecord<>( kafkaTopicConfig.getEmail( ), TEMPLATE_KEY, payload );
                    log.info( "Sending notification to Kafka. entityType='{}', topic='{}'", entityType, kafkaTopicConfig.getEmail() );
                    return sender.send( Mono.just( SenderRecord.create( record, null ) ) ).next( );
                } ).doOnNext( result -> {

                    if ( result != null && result.recordMetadata( ) != null ) {
                        log.info( "Kafka publish successful. entityType='{}', topic='{}', partition={}, offset={}", entityType, result.recordMetadata( ).topic( ), result.recordMetadata( ).partition( ), result.recordMetadata( ).offset( ) );
                    } else {
                        log.info( "Warning-Kafka publish completed but metadata is null for entityType='{}'", entityType );
                    }
                } ).doOnSuccess( result -> log.info( "Notification workflow completed successfully for entityType='{}'", entityType ) ).doOnError( error -> log.info( "Err-Notification workflow failed. entityType='{}', topic='{}', error='{}'", entityType, kafkaTopicConfig.getEmail(), error.getMessage( ),
                        error ) )
                .onErrorResume( error -> {
                    log.info( "Err-Suppressed notification failure for entityType='{}'. Application flow will continue.", entityType, error );
                    return Mono.empty( );
                } ).then( );
    }

    private Object normalizeKeys ( String rawEntityType, Object data ) {
        if ( data == null ) {
            return null;
        }
        Map< String, String > labels = formLabelsProperties.getForms( ).get( rawEntityType );
        if ( labels == null || labels.isEmpty( ) ) {
            return data;
        }
        Map< String, Object > source = objectMapper.convertValue( data, new TypeReference< LinkedHashMap< String, Object > >( ) {} );
        Map< String, Object > normalized = new LinkedHashMap<>( );
        source.forEach( ( key, value ) -> normalized.put( labels.getOrDefault( key, key ), value ) );
        return normalized;
    }
}