package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;

import java.util.List;

public class SlideAnalysisReportTestData {

    public static final String ANALYSIS_ID = "report-1";
    public static final String MISSING_ANALYSIS_ID = "report-404";
    public static final String DEVICE_SERIAL_NUMBER = "SS12118";
    public static final String QA_SLIDE_BARCODE = "10224";
    public static final String TEST_PATH_QA_DICOM_STORE = " projects/prj-d-path-integration-cs1h/locations/us-central1/datasets/digital-pathology-dataset/dicomStores/digital-pathology-pathqa-dicomstore";
    public static final String TEST_DICOM_WEB_URL = "https://healthcare.googleapis.com/v1";

    public static SlideAnalysisReport analysisReport() {
        SlideAnalysisReport analysisReport = new SlideAnalysisReport();
        analysisReport.setAnalysisId("report-1");
        analysisReport.setStatus("Completed");
        return analysisReport;
    }

    public static List<SlideAnalysisReport> reportList() {

        SlideAnalysisReport report1 = new SlideAnalysisReport();
        report1.setAnalysisId(ANALYSIS_ID);
        report1.setDeviceSerialNumber(DEVICE_SERIAL_NUMBER);

        SlideAnalysisReport report2 = new SlideAnalysisReport();
        report2.setAnalysisId("report-2");
        report2.setDeviceSerialNumber(DEVICE_SERIAL_NUMBER);

        return List.of(report1, report2);
    }

    public static SlideAnalysisReport analysisReport(String analysisId) {
        SlideAnalysisReport report = new SlideAnalysisReport();
        report.setAnalysisId(analysisId);
        report.setDeviceSerialNumber(DEVICE_SERIAL_NUMBER);
        return report;
    }

    public static SlideAnalysisMessage kafkaMessage = new SlideAnalysisMessage(
            QA_SLIDE_BARCODE,
            "1.2.3.study",
            "1.2.3.series",
            DEVICE_SERIAL_NUMBER,
            "/dicomstore"
    );
}
