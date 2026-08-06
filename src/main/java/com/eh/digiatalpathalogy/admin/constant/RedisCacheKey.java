package com.eh.digiatalpathalogy.admin.constant;

public class RedisCacheKey {
    private RedisCacheKey() {
    }

    public static final String SLIDE_BARCODE_PREFIX = "path-qa-slide:barcode:";
    public static final String ANALYSIS_ID_PREFIX = "report:analysisId:";
    public static final String SCANNER_DEVICE_PREFIX = "scanner::deviceSerialNumber::";
    public static final String METADATA_HOSPITAL = "metadata:";
    public static final String SLIDE_SCAN_STATUS_PREFIX = "slide-scan-status:barcode:";
    public static final String AUTH_CONFIG = "config:auth:";
    public static final String SERVICE_HOST_INFO = "service:host:";
    public static final String DICOM_RECEIVER_PATH_QA_SLIDE_BARCODE_PREFIX= "path-slide:barcode:";
    public static final String DICOM_RECEIVER_SCANNER_DEVICE_PREFIX= "scanner:deviceSerial:";
    public static final String ACTIVE_SCAN_KEY = "slide:status:active";

}
