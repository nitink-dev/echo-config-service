package com.eh.digiatalpathalogy.admin.model;

import java.util.Arrays;

/**
 * Maps the raw entityType/application identifiers used internally (e.g. "qaSlide",
 * "eh-lis-connector") to a human-readable name for use in notification emails.
 */
public enum NotificationEntityType {
    QA_SLIDE("qaSlide", "QA Slide"),
    SCANNER("scanner", "Scanner"),
    DICOM_STORE("dicomStore", "DICOM Store"),
    DICOM_RECEIVER("eh-dicom-receiver", "DICOM Receiver"),
    LIS_CONNECTOR("eh-lis-connector", "LIS Connector"),
    DICOM_ENRICHER("eh-dicom-enricher", "DICOM Enricher"),
    HL7_CONNECTOR("eh-hl7-connector", "HL7 Connector"),
    EXPORT_SERVICE("eh-export-service", "Export Service"),
    EMAIL_SERVICE("eh-email-service", "Email Service"),
    SYNAPSE("synapse", "Synapse"),
    LIS("lis", "LIS");

    private final String key;
    private final String displayName;

    NotificationEntityType(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves a raw entityType/application key to its display name.
     * Falls back to the raw key itself if it isn't mapped yet, so unmapped
     * identifiers never break the notification flow.
     */
    public static String toDisplayName(String key) {
        return Arrays.stream(values())
                .filter(type -> type.key.equalsIgnoreCase(key))
                .findFirst()
                .map(NotificationEntityType::getDisplayName)
                .orElse(key);
    }
}