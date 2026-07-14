package com.eh.digiatalpathalogy.admin.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "dicom_instances")
public class DicomInstance {

    @Id
    private ObjectId id;

    private String originalStudyInstanceUid;
    private String intermediateStoragePath;
    private String processingStatus;
    private String deviceSerialNumber;
    private String barcode;
    private String sopInstanceUid;
    private String seriesInstanceUid;
    private String actualStudyInstanceUid;
    private String caseNumber;
    private String errorMessage;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant enrichmentTimestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant dicomInstanceReceivedTimestamp;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getOriginalStudyInstanceUid() {
        return originalStudyInstanceUid;
    }

    public void setOriginalStudyInstanceUid(String originalStudyInstanceUid) {
        this.originalStudyInstanceUid = originalStudyInstanceUid;
    }

    public String getIntermediateStoragePath() {
        return intermediateStoragePath;
    }

    public void setIntermediateStoragePath(String intermediateStoragePath) {
        this.intermediateStoragePath = intermediateStoragePath;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Instant getDicomInstanceReceivedTimestamp() {
        return dicomInstanceReceivedTimestamp;
    }

    public void setDicomInstanceReceivedTimestamp(Instant dicomInstanceReceivedTimestamp) {
        this.dicomInstanceReceivedTimestamp = dicomInstanceReceivedTimestamp;
    }

    public String getDeviceSerialNumber() {
        return deviceSerialNumber;
    }

    public void setDeviceSerialNumber(String deviceSerialNumber) {
        this.deviceSerialNumber = deviceSerialNumber;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getSeriesInstanceUid() {
        return seriesInstanceUid;
    }

    public void setSeriesInstanceUid(String seriesInstanceUid) {
        this.seriesInstanceUid = seriesInstanceUid;
    }

    public String getActualStudyInstanceUid() {
        return actualStudyInstanceUid;
    }

    public void setActualStudyInstanceUid(String actualStudyInstanceUid) {
        this.actualStudyInstanceUid = actualStudyInstanceUid;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public Instant getEnrichmentTimestamp() {
        return enrichmentTimestamp;
    }

    public void setEnrichmentTimestamp(Instant enrichmentTimestamp) {
        this.enrichmentTimestamp = enrichmentTimestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
