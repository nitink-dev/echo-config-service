package com.eh.digiatalpathalogy.admin.model.scanstatus;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SseEventMessage<T>(String eventType, String eventId, T payload) {

    public static <T> SseEventMessage<T> of(String eventType, String eventId, T payload) {
        return new SseEventMessage<>(eventType, eventId, payload);
    }

    public static SseEventMessage<Void> signal(String eventType) {
        return new SseEventMessage<>(eventType, null, null);
    }
}