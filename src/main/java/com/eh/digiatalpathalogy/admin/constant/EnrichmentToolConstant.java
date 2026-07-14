package com.eh.digiatalpathalogy.admin.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class EnrichmentToolConstant {

    private EnrichmentToolConstant() {
    }

    public static final String SOURCE_NATIVE = "native";
    public static final String COMMON = "common";
    public static final String EH_LIS_CONNECTOR = "eh-lis-connector";
    public static final String EH_DICOM_ENRICHER = "eh-dicom-enricher";
    public static final String EH_HL7_CONNECTOR = "eh-hl7-connector";
    public static final String EH_EXPORT_SERVICE = "eh-export-service";
    public static final String SYNAPSE = "synapse";
    public static final Set<String> ARRAY_FIELDS = new HashSet<>(Arrays.asList("emailTo", "emailFrom", "emailIbexTo"));
    public static final String SERVICE_IP_ADDRESS = "serviceIpAddress";
    public static final String EH_ADMIN_CONSOLE = "eh-admin-console";

}
