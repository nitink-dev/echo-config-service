package com.eh.digiatalpathalogy.admin.model.scanstatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashSet;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlideScanProgressEvent(String accessionNumber, String slideBarcode,
                                     String seriesId,
                                     String deviceSerialNumber, String scanStatus, String sourceService,
                                     Double progressPercent,
                                     LinkedHashSet<ScanStatus> progressEvents, String errorMessage, String eventType) {

    public SlideScanProgressEvent(String accessionNumber, String slideBarcode, String seriesId, String deviceSerialNumber,
                                  String scanStatus, String sourceService, Double progressPercent, String errorMessage) {
        this(accessionNumber, slideBarcode, seriesId, deviceSerialNumber, scanStatus, sourceService, progressPercent, null, errorMessage, null);
    }

    public SlideScanProgressEvent(String eventType) {
        this(null, null, null, null, null, null, null, null, null, eventType);
    }
}