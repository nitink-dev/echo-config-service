package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.config.EnrichmentToolConfig;

import java.util.HashMap;
import java.util.Map;

public class EnrichmentToolTestData {

    public static EnrichmentToolConfig buildRealConfig() {

        EnrichmentToolConfig config = new EnrichmentToolConfig();
        Map<String, EnrichmentToolConfig.AppMapping> apps = new HashMap<>();

        apps.put("eh-dicom-receiver", mapping(Map.of(
                "native", Map.of(
                        "aet", "storescp.aetitle",
                        "port", "storescp.port"
                )
        )));

        apps.put("eh-hl7-connector", mapping(Map.of(
                "common", Map.of("appName", "message.config.sending-app"),
                "native", Map.of("receive-port", "tcp.incoming.port")
        )));

        apps.put("eh-export-service", mapping(Map.of(
                "native", Map.of(
                        "synapseServerFolder", "synapse.server.folder"
                )
        )));

        apps.put("eh-email-service", mapping(Map.of(
                "native", Map.of(
                        "emailTo", "email.to",
                        "emailFrom", "email.from"
                )
        )));

        apps.put("synapse", mapping(Map.of(
                "eh-hl7-connector", Map.of(
                        "native.receivingAppName", "message.config.receiving-app"
                ),
                "eh-export-service", Map.of(
                        "native.synapseServerFolder", "synapse.server.folder"
                )
        )));

        config.setApplications(apps);
        return config;
    }

    private static EnrichmentToolConfig.AppMapping mapping(Map<String, Map<String, String>> m) {
        EnrichmentToolConfig.AppMapping app = new EnrichmentToolConfig.AppMapping();
        app.setMappings(m);
        return app;
    }
}
