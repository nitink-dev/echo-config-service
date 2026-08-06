package com.eh.digiatalpathalogy.admin.entity;

import com.eh.digiatalpathalogy.admin.model.SlideAnalysisError;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.SLIDE_ANALYSIS_REPORT;

@Document(collection = SLIDE_ANALYSIS_REPORT)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlideAnalysisReport {

    @Id
    private String id;
    @NotBlank
    private String analysisId;
    private String slideBarcode;
    private String deviceSerialNumber;
    private String dicomSeriesPath;
    private String status;
    private SlideAnalysisResult result;
    private SlideAnalysisError error;

    @CreatedDate
    private Date createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public @NotBlank String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(@NotBlank String analysisId) {
        this.analysisId = analysisId;
    }

    public String getDicomSeriesPath() {
        return dicomSeriesPath;
    }

    public void setDicomSeriesPath(String dicomSeriesPath) {
        this.dicomSeriesPath = dicomSeriesPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public SlideAnalysisResult getResult() {
        return result;
    }

    public void setResult(SlideAnalysisResult result) {
        this.result = result;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

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

    public SlideAnalysisError getError() {
        return error;
    }

    public void setError(SlideAnalysisError error) {
        this.error = error;
    }
}