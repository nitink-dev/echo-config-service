package com.eh.digiatalpathalogy.admin.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SlideScanStatusConstant {

    private SlideScanStatusConstant() {
    }

    public static final String SCAN_COMPLETED = "completed";
    public static final String SCAN_FAILED = "failed";
    public static final String IN_PROGRESS = "in-progress";
    public static final String SCAN_WARNING_COMPLETED = "warning-completed";
    public static final String IBEX_WARNING_COMPLETE = "ibex-warning-completed";
    public static final String TIMEOUT_WARNING_COMPLETED = "timeout-warning-completed";
    public static final String ENRICHMENT_IN_PROGRESS_STATUS = "enrichment-in-progress";
    public static final List<String> INACTIVE_SCAN_STATUSES = List.of(SCAN_COMPLETED, SCAN_FAILED, IBEX_WARNING_COMPLETE, SCAN_WARNING_COMPLETED);
    public static final List<String> COMPLETE_SCAN_STATUSES = List.of(SCAN_COMPLETED, IBEX_WARNING_COMPLETE, SCAN_WARNING_COMPLETED);
    public static final String EH_DP_EXPORT_SERVICE = "eh-dp-export-service";
    public static final String EH_IBEX_SERVICE = "eh-ibex-adapter";
    public static final String SCAN_STATUS_EXPORTED = "exported";
    public static final String EH_DP_HL7_CONNECTOR = "eh-dp-hl7-connector";
    public static final Set<String> SCAN_FAILED_STATUS = new HashSet<>(Arrays.asList("dicom-enriched-failed", "export-failed", "ibex-invalid-data", "ibex-slide-download-failed", "ibex-classification-failed", "synapse-failed", "ibex-slide-creation-failed", "ibex-files-download-failed"));
    public static final String SLIDE_BARCODE = "slideBarcode";
    public static final String SERIES_ID = "seriesId";
    public static final String SYNAPSE_EXPORT_FAILED = "synapse-export-failed";
    public static final List<String> WARNING_COMPLETED = List.of(IBEX_WARNING_COMPLETE, SYNAPSE_EXPORT_FAILED,TIMEOUT_WARNING_COMPLETED, SCAN_WARNING_COMPLETED);
    public static final String UPDATED_AT = "updatedAt";
    public static final String PROGRESS_EVENTS = "progressEvents";
    public static final String SLIDE_STATUS = "slide_scan_status";
    public static final String RESEARCH_EVENT = "research_event";
    public static final String HEARTBEAT = "heartbeat";
    public static final String ACTIVE_SCAN_KEY = "slide:status:active";
    public static final String LOCK_KEY = "lock:slide:timeout";

}

