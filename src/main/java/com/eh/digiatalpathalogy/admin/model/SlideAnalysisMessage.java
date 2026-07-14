package com.eh.digiatalpathalogy.admin.model;

public record SlideAnalysisMessage(String barcodeValue, String studyInstanceUid, String seriesInstanceUid,
                                   String deviceSerialNumber, String dicomStore) {
}
