package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.entity.DicomInstance;
import com.eh.digiatalpathalogy.admin.entity.SlideScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.DicomInstanceDto;
import com.eh.digiatalpathalogy.admin.model.scanstatus.ScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanStatusDto;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.*;

import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.SLIDE_STATUS;

public class SlideScanStatusTestData {

    public static DicomInstanceDto dicomIngested(String barcode) {
        Instant received = Instant.parse("2026-02-15T10:15:30Z");
        return new DicomInstanceDto(UUID.randomUUID().toString(), "1.2.840.113619.2.55.3.2831164357.123.1589", "gs://ingestion-bucket/",
                "LIS_REQUEST_GENERATED", received, "SS35106", barcode, "1.2.840.113619.2.55.3.2831164357.123.1589.1", "1.2.840.113619.2.55.3.2831164357.123.2",
                null, null, null, null
        );
    }

    public static DicomInstanceDto dicomEnrichedDto(String barcode) {
        Instant received = Instant.parse("2026-02-15T10:15:30Z");
        Instant enriched = Instant.parse("2026-02-15T10:20:00Z");
        return new DicomInstanceDto(UUID.randomUUID().toString(), "1.2.840.113619.2.55.3.2831164357.123.1589", "gs://processing-bucket/",
                "ENRICHED", received, "SS35106", barcode, "1.2.840.113619.2.55.3.2831164357.123.1589.2", "1.2.840.113619.2.55.3.2831164357.123.3",
                "1.2.840.113619.2.55.3.2831164357.999.1000",
                "CASE-" + barcode, enriched, null
        );
    }

    public static DicomInstanceDto dicomFailed(String barcode) {
        Instant received = Instant.parse("2026-02-15T10:15:30Z");
        return new DicomInstanceDto(UUID.randomUUID().toString(), "1.2.840.113619.2.55.3.2831164357.123.1589", "gs://processing-bucket/", "ENRICHED_FAILED",
                received, "SS35106", barcode, "1.2.840.113619.2.55.3.2831164357.123.1589.9", "1.2.840.113619.2.55.3.2831164357.123.9",
                null, null, null, "Max retries failed."
        );
    }

    public static SlideScanStatusDto slideScanStatus(String slideBarcode, String scanStatus) {
        Instant createdAt = Instant.parse("2026-02-15T10:15:30Z");
        Instant updatedAt = Instant.parse("2026-02-15T10:20:00Z");
        return new SlideScanStatusDto(UUID.randomUUID().toString(), "ELN-12-1234567", slideBarcode, "test-series-id","SS35106", scanStatus, 100.0,
                createdAt, updatedAt, null);
    }

    public static Map<String, Map<String, Double>> slideScanConfiguration() {

        Map<String, Map<String, Double>> slideScanConfiguration = new HashMap<>();

        Map<String, Double> receiver = new HashMap<>();
        receiver.put("enrichment-in-progress", 2.0);
        slideScanConfiguration.put("eh-dp-dicom-receiver", receiver);

        Map<String, Double> enricher = new HashMap<>();
        enricher.put("enrichment-in-progress", 2.0);
        enricher.put("enrichment-completed", 24.0);
        enricher.put("dicom-enriched-failed", 0.0);
        slideScanConfiguration.put("eh-dp-dicom-enricher", enricher);

        Map<String, Double> exportSvc = new HashMap<>();
        exportSvc.put("export-in-progress", 10.0);
        exportSvc.put("synapse-exported", 10.0);
        exportSvc.put("exported", 10.0);
        exportSvc.put("export-failed", -20.0);
        slideScanConfiguration.put("eh-dp-export-service", exportSvc);

        Map<String, Double> hl7 = new HashMap<>();
        hl7.put("synapse-started", 5.0);
        hl7.put("synapse-completed", 5.0);
        hl7.put("synapse-failed", -5.0);
        slideScanConfiguration.put("eh-dp-hl7-connector", hl7);

        Map<String, Double> ibexConfig = new HashMap<>();
        ibexConfig.put("ibex-invalid-data", -5.0);
        ibexConfig.put("ibex-slide-download-completed", 5.0);
        ibexConfig.put("ibex-slide-download-failed", -5.0);
        ibexConfig.put("ibex-classification-finished", 5.0);
        ibexConfig.put("ibex-warning-completed", 5.0);
        ibexConfig.put("ibex-classification-failed", -5.0);
        ibexConfig.put("ibex-invalid-slide", -5.0);
        ibexConfig.put("ibex-slide-creation-failed", -5.0);
        ibexConfig.put("ibex-files-download-failed", -5.0);
        slideScanConfiguration.put("eh-ibex-adapter", ibexConfig);

        return slideScanConfiguration;
    }

    public static Set<String> validScanStatus() {
        return new HashSet<>(Arrays.asList(
                "enrichment-in-progress", "enrichment-completed", "dicom-enriched-failed",
                "export-in-progress", "synapse-exported", "exported", "export-failed",
                "synapse-started", "synapse-completed", "synapse-failed",
                "ibex-invalid-data", "ibex-slide-download-completed", "ibex-slide-download-failed", "ibex-classification-finished",
                "ibex-warning-completed", "ibex-classification-failed", "ibex-invalid-slide", "ibex-slide-creation-failed", "ibex-files-download-failed"
        ));
    }


    public static DicomInstance enrichedDicomInstance(String barcode) {

        Instant received = Instant.parse("2026-02-15T10:15:30Z");
        Instant enriched = Instant.parse("2026-02-15T10:20:00Z");

        DicomInstance dicomInstance = new DicomInstance();
        dicomInstance.setBarcode(barcode);
        dicomInstance.setProcessingStatus("ENRICHED");
        dicomInstance.setIntermediateStoragePath("gs://processing-bucket/" + barcode + "/STAGING");
        dicomInstance.setDicomInstanceReceivedTimestamp(received);
        dicomInstance.setEnrichmentTimestamp(enriched);
        dicomInstance.setActualStudyInstanceUid("1.2.840.113619.2.55.3.2831164357.999.1000");
        dicomInstance.setCaseNumber("CASE-" + barcode);
        dicomInstance.setErrorMessage(null);
        return dicomInstance;
    }

    public static SlideScanStatusDto slideScanStatusDto(String barcode, String status, double pct) {
        Instant createdAt = Instant.parse("2026-02-15T10:15:30Z");
        Instant updatedAt = Instant.parse("2026-02-15T10:20:00Z");
        return new SlideScanStatusDto(UUID.randomUUID().toString(), "ELN-12_12345", barcode,"test-series-id", "SS35106", status, pct,
                createdAt, updatedAt, null);
    }

    public static SlideScanProgressEvent sseEnrichmentInProgress(String barcode,Double progressPercent) {
        return new SlideScanProgressEvent(null, barcode,"test-series-id",  "SS35106", "enrichment-in-progress", "eh-dp-dicom-receiver", progressPercent, null);
    }

    public static SlideScanProgressEvent sseEnrichmentInProgressWithParameter(String barcode, String scanStatus, String sourceService, Double progressPercent,LinkedHashSet<ScanStatus> progressEvent) {
        return new SlideScanProgressEvent(null, barcode,"test-series-id", "SS35106", scanStatus, sourceService, progressPercent,progressEvent,null,SLIDE_STATUS);
    }

    public static SlideScanProgressEvent invalidSseEvent(String barcode) {
        return new SlideScanProgressEvent(null, barcode, "test-series-id","SS35106", "started", "eh-dp-dicom-receiver", 26.0, null);
    }

    public static SlideScanStatus slideScanStatusEntity(String barcode,String scanStatus,Double progressPercent) {
        SlideScanStatus slideScanStatus = new SlideScanStatus();
        slideScanStatus.setId(new ObjectId());
        slideScanStatus.setSlideBarcode(barcode);
        slideScanStatus.setDeviceSerialNumber("SS35106");
        slideScanStatus.setScanStatus(scanStatus);
        slideScanStatus.setProgressPercent(progressPercent);
        slideScanStatus.setUpdatedAt(Instant.now());
        return slideScanStatus;
    }

}
