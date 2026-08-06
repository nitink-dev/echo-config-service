package com.eh.digiatalpathalogy.admin.model;

public class SlideAnalysisResult {

    private String pdfReportUrl;
    private String csvDataFileUrl;



    public String getPdfReportUrl() {
        return pdfReportUrl;
    }

    public void setPdfReportUrl(String pdfReportUrl) {
        this.pdfReportUrl = pdfReportUrl;
    }

    public String getCsvDataFileUrl() {
        return csvDataFileUrl;
    }

    public void setCsvDataFileUrl(String csvDataFileUrl) {
        this.csvDataFileUrl = csvDataFileUrl;
    }
}
