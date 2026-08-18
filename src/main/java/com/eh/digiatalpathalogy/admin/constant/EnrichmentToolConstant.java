package com.eh.digiatalpathalogy.admin.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class EnrichmentToolConstant {

    private EnrichmentToolConstant() {
    }

    public static final String EH_EXPORT_SERVICE = "eh-export-service";
    public static final Set<String> ARRAY_FIELDS = new HashSet<>(Arrays.asList("emailTo", "emailFrom", "emailIbexTo"));
    public static final String SERVICE_IP_ADDRESS = "serviceIpAddress";
    public static final String EH_ADMIN_CONSOLE = "eh-admin-console";
    public static final String CONFIG_SOURCE_GIT = "git";
    public static final String CONFIG_SOURCE_VAULT = "/vault/kv";


}
