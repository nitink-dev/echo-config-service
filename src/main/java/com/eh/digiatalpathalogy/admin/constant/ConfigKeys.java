package com.eh.digiatalpathalogy.admin.constant;

import java.util.Map;

public final class ConfigKeys {
    private ConfigKeys() {
    }

    // Redis Keys
    public static final String DICOM_WEB_URL = "config:gcp:dicom-web-url";
    public static final String PATH_QA_DICOM_STORE = "config:gcp-config.pathqa-store-url";
    public static final String RESEARCH_DICOM_STORE = "config:gcp-config.research-store-url";

    // Config Key Paths
    public static final String DICOM_WEB_URL_PATH = "gcp-config.dicom-web-url";
    public static final String PATH_QA_DICOM_STORE_PATH = "gcp-config.pathqa-store-url";
    public static final String RESEARCH_DICOM_STORE_PATH = "gcp-config.research-store-url";

    public static final String DEFAULT_APPLICATION = "eh-admin-console";
    public static final String EH_DICOM_RECEIVER = "eh-dicom-receiver";
    public static final String DEFAULT_PROFILE = "default";


    private static final Map<String, String> defaultConfigKeys = Map.of(
            DICOM_WEB_URL, DICOM_WEB_URL_PATH,
            PATH_QA_DICOM_STORE, PATH_QA_DICOM_STORE_PATH,
            RESEARCH_DICOM_STORE, RESEARCH_DICOM_STORE_PATH
    );

    private static final Map<String, Map<String, String>> appConfigs = Map.of(
            DEFAULT_APPLICATION, defaultConfigKeys
    );

    public static Map<String, String> configByApplication(String application) {
        return appConfigs.get(application);
    }
}
