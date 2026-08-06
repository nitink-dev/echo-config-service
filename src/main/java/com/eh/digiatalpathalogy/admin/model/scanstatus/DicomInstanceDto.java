package com.eh.digiatalpathalogy.admin.model.scanstatus;

import com.eh.digiatalpathalogy.admin.entity.DicomInstance;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record DicomInstanceDto(
        String id,
        String originalStudyInstanceUid,
        String intermediateStoragePath,
        String processingStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant dicomInstanceReceivedTimestamp,
        String deviceSerialNumber,
        String barcode,
        String sopInstanceUid,
        String seriesInstanceUid,
        String actualStudyInstanceUid,
        String caseNumber,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant enrichmentTimestamp,
        String errorMessage
) {
    public static DicomInstanceDto from(DicomInstance d) {
        return new DicomInstanceDto(
                d.getId() != null ? d.getId().toHexString() : null,
                d.getOriginalStudyInstanceUid(),
                d.getIntermediateStoragePath(),
                d.getProcessingStatus(),
                d.getDicomInstanceReceivedTimestamp(),
                d.getDeviceSerialNumber(),
                d.getBarcode(),
                d.getSopInstanceUid(),
                d.getSeriesInstanceUid(),
                d.getActualStudyInstanceUid(),
                d.getCaseNumber(),
                d.getEnrichmentTimestamp(),
                d.getErrorMessage()
        );
    }
}
