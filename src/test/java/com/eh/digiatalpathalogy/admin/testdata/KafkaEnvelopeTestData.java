package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.model.KafkaEnvelope;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;

public class KafkaEnvelopeTestData {

    public static KafkaEnvelope< SlideScanProgressEvent > sampleScanEvent() {
        SlideScanProgressEvent slideScanProgressEvent = new SlideScanProgressEvent( "ACC-001", "BARCODE123", "SERIES-001", "DEVICE-001", "IN_PROGRESS", "scanner-service", 55.5, null );
        return new KafkaEnvelope<>(
                "scan-topic",
                "BARCODE123",
                0,
                101L,
                slideScanProgressEvent,
                "BARCODE123",
                null
        );
    }

    public static KafkaEnvelope< SlideAnalysisMessage > sampleQaEvent() {
        SlideAnalysisMessage message = new SlideAnalysisMessage( "BARCODE123", "1.2.3", "1.2.3.4", "DEVICE-001", "store1" );
        return new KafkaEnvelope<>(
                "qa-topic",
                "BARCODE123",
                0,
                102L,
                message,
                "BARCODE123",
                null
        );
    }
}