package com.eh.digiatalpathalogy.admin.model;


import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverRecord;

public record KafkaEnvelope<T>(String topic, String key, int partition, long offset, T payload, String barcodeKey,
                            ReceiverOffset offsetHandle) {

    public static <T> KafkaEnvelope<T> from(ReceiverRecord<String, T> r) {
        return new KafkaEnvelope<>(r.topic(), r.key(), r.partition(), r.offset(), r.value(), r.key(), r.receiverOffset());
    }
}