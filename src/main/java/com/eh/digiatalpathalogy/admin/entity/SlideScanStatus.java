package com.eh.digiatalpathalogy.admin.entity;

import com.eh.digiatalpathalogy.admin.model.scanstatus.ScanStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.LinkedHashSet;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.SLIDE_SCAN_STATUS;
import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.PROGRESS_EVENTS;

@Document(collection = SLIDE_SCAN_STATUS)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlideScanStatus {

    @Id
    private ObjectId id;

    private String accessionNumber;
    private String slideBarcode;
    private String deviceSerialNumber;
    private String scanStatus;
    private Double progressPercent;
    @Field(PROGRESS_EVENTS)
    private LinkedHashSet<ScanStatus> progressEvents;
    private String seriesId;
    private LinkedHashSet<SlideScanStatus> scanHistory;

    @CreatedDate
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @LastModifiedDate
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    public String getSlideBarcode() {
        return slideBarcode;
    }

    public void setSlideBarcode(String slideBarcode) {
        this.slideBarcode = slideBarcode;
    }

    public String getDeviceSerialNumber() {
        return deviceSerialNumber;
    }

    public void setDeviceSerialNumber(String deviceSerialNumber) {
        this.deviceSerialNumber = deviceSerialNumber;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public Double getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Double progressPercent) {
        this.progressPercent = progressPercent;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public LinkedHashSet<SlideScanStatus> getScanHistory() {
        return scanHistory;
    }

    public void setScanHistory(LinkedHashSet<SlideScanStatus> scanHistory) {
        this.scanHistory = scanHistory;
    }

    public LinkedHashSet<ScanStatus> getProgressEvents() {
        return progressEvents;
    }

    public void setProgressEvents(LinkedHashSet<ScanStatus> progressEvents) {
        this.progressEvents = progressEvents;
    }
}
