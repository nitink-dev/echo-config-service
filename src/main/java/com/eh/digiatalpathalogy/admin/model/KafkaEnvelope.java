package com.eh.digiatalpathalogy.admin.model;


import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;

public record KafkaEnvelope(String topic, String key, int partition, long offset, String payload, String barcodeKey,
                            ReceiverOffset offsetHandle) {

    public static KafkaEnvelope from(ReceiverRecord<String, String> r) {
        return new KafkaEnvelope(r.topic(), r.key(), r.partition(), r.offset(), r.value(), r.key(), r.receiverOffset());
    }
}