package com.eh.digiatalpathalogy.admin.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.api.services.healthcare.v1.model.Dataset;
import com.google.api.services.healthcare.v1.model.DicomStore;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Service
@RefreshScope
public class DicomStoreService {

    private static final Logger log = LoggerFactory.getLogger(DicomStoreService.class);
    private final CloudHealthcare healthcare;

    @Value("${gcp-config.project-id}")
    private String projectId;

    @Value("${gcp-config.location-id}")
    private String locationId;

    public DicomStoreService() throws IOException, GeneralSecurityException {
        this.healthcare = initializeHealthcareClient();
    }

    public DicomStoreService(CloudHealthcare healthcare) {
        this.healthcare = healthcare;
    }

    private CloudHealthcare initializeHealthcareClient() throws IOException, GeneralSecurityException {

        try {
            var credentials = GoogleCredentials.getApplicationDefault().createScoped(CloudHealthcareScopes.CLOUD_PLATFORM);
            return new CloudHealthcare.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(credentials))
                    .setApplicationName(projectId)
                    .build();
        } catch (Exception e) {
            log.error("GCP Healthcare client initialization failed. Check credentials configuration.");
            return null;
        }
    }

    public Map<String, List<String>> getAllDatasetsWithDicomStores() {
        return getAllDatasetsWithDicomStores(projectId, locationId);
    }

    private Map<String, List<String>> getAllDatasetsWithDicomStores(String projectId, String locationId) {
        var datasetStoreMap = new HashMap<String, List<String>>();

        if (healthcare == null) {
            log.warn("GCP Healthcare client not configured. Skipping dataset fetch.");
            return datasetStoreMap;
        }

        try {
            for (var dataset : fetchDatasets(projectId, locationId)) {
                var datasetName = dataset.getName();
                var datasetId = extractId(datasetName);
                var dicomStores = fetchDicomStoreNames(datasetName);
                datasetStoreMap.put(datasetId, dicomStores);
            }
        } catch (IOException e) {
            log.error("Error retrieving datasets or DICOM stores", e);
        }

        return datasetStoreMap;
    }

    private List<Dataset> fetchDatasets(String projectId, String locationId) throws IOException {
        var parent = "projects/%s/locations/%s".formatted(projectId, locationId);
        var response = healthcare.projects().locations().datasets().list(parent).execute();
        return Optional.ofNullable(response.getDatasets()).orElse(List.of());
    }

    private List<String> fetchDicomStoreNames(String datasetName) throws IOException {
        var response = healthcare.projects()
                .locations()
                .datasets()
                .dicomStores()
                .list(datasetName)
                .execute();

        return Optional.ofNullable(response.getDicomStores())
                .orElse(List.of())
                .stream()
                .map(DicomStore::getName)
                .toList();
    }

    private String extractId(String fullName) {
        return fullName.substring(fullName.lastIndexOf("/") + 1);
    }
}

