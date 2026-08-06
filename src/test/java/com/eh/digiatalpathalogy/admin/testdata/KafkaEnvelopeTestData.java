package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;

public class KafkaEnvelopeTestData {

    public static KafkaEnvelope sampleScanEvent() {
        return new KafkaEnvelope(
                "scan-topic",
                "BARCODE123",
                0,
                101L,
                "{ \"accessionNumber\":\"ACC-001\", " +
                        "\"slideBarcode\":\"BARCODE123\", " +
                        "\"seriesId\":\"SERIES-001\", " +
                        "\"deviceSerialNumber\":\"DEVICE-001\", " +
                        "\"scanStatus\":\"IN_PROGRESS\", " +
                        "\"sourceService\":\"scanner-service\", " +
                        "\"progressPercent\":55.5 }",
                "BARCODE123",
                null
        );
    }

    public static KafkaEnvelope sampleQaEvent() {
        return new KafkaEnvelope(
                "qa-topic",
                "BARCODE123",
                0,
                102L,
                "{ \"barcodeValue\":\"BARCODE123\", " +
                        "\"studyInstanceUid\":\"1.2.3\", " +
                        "\"seriesInstanceUid\":\"1.2.3.4\", " +
                        "\"deviceSerialNumber\":\"DEVICE-001\", " +
                        "\"dicomStore\":\"store1\" }",
                "BARCODE123",
                null
        );
    }
}