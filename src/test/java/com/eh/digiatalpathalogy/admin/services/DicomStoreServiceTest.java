package com.eh.digiatalpathalogy.admin.services;

import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.model.Dataset;
import com.google.api.services.healthcare.v1.model.DicomStore;
import com.google.api.services.healthcare.v1.model.ListDatasetsResponse;
import com.google.api.services.healthcare.v1.model.ListDicomStoresResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DicomStoreServiceTest {

    @Mock
    private CloudHealthcare healthcare;
    @Mock
    private CloudHealthcare.Projects projects;
    @Mock
    private CloudHealthcare.Projects.Locations locations;
    @Mock
    private CloudHealthcare.Projects.Locations.Datasets datasets;
    @Mock
    private CloudHealthcare.Projects.Locations.Datasets.DicomStores dicomStores;

    @Mock
    private CloudHealthcare.Projects.Locations.Datasets.List datasetsListReq;
    @Mock
    private CloudHealthcare.Projects.Locations.Datasets.DicomStores.List dicomStoresListReq;

    private DicomStoreService service;

    @BeforeEach
    void setUp() {
        service = new DicomStoreService(healthcare);

        ReflectionTestUtils.setField(service, "projectId", "p1");
        ReflectionTestUtils.setField(service, "locationId", "us");

        when(healthcare.projects()).thenReturn(projects);
        when(projects.locations()).thenReturn(locations);
        when(locations.datasets()).thenReturn(datasets);

    }

    @Test
    @DisplayName("getAllDatasetsWithDicomStores() -> happy path returns datasetId -> dicom store names")
    void getAllDatasetsWithDicomStores_happyPath() throws Exception {
        Dataset ds1 = new Dataset().setName("projects/p1/locations/us/datasets/ds1");
        Dataset ds2 = new Dataset().setName("projects/p1/locations/us/datasets/ds2");

        when(datasets.dicomStores()).thenReturn(dicomStores);
        when(datasets.list("projects/p1/locations/us")).thenReturn(datasetsListReq);
        when(datasetsListReq.execute()).thenReturn(new ListDatasetsResponse().setDatasets(List.of(ds1, ds2)));

        DicomStore s1 = new DicomStore().setName("projects/p1/locations/us/datasets/ds1/dicomStores/storeA");
        DicomStore s2 = new DicomStore().setName("projects/p1/locations/us/datasets/ds1/dicomStores/storeB");
        when(dicomStores.list("projects/p1/locations/us/datasets/ds1")).thenReturn(dicomStoresListReq);
        when(dicomStoresListReq.execute()).thenReturn(new ListDicomStoresResponse().setDicomStores(List.of(s1, s2)));

        CloudHealthcare.Projects.Locations.Datasets.DicomStores.List ds2Req =
                mock(CloudHealthcare.Projects.Locations.Datasets.DicomStores.List.class);
        when(dicomStores.list("projects/p1/locations/us/datasets/ds2")).thenReturn(ds2Req);

        DicomStore s3 = new DicomStore().setName("projects/p1/locations/us/datasets/ds2/dicomStores/storeX");
        when(ds2Req.execute()).thenReturn(new ListDicomStoresResponse().setDicomStores(List.of(s3)));

        Map<String, List<String>> result = service.getAllDatasetsWithDicomStores();

        assertThat(result).containsOnlyKeys("ds1", "ds2");
        assertThat(result.get("ds1")).containsExactly(
                "projects/p1/locations/us/datasets/ds1/dicomStores/storeA",
                "projects/p1/locations/us/datasets/ds1/dicomStores/storeB"
        );
        assertThat(result.get("ds2")).containsExactly(
                "projects/p1/locations/us/datasets/ds2/dicomStores/storeX"
        );

        verify(datasets, times(1)).list("projects/p1/locations/us");
        verify(dicomStores, times(1)).list("projects/p1/locations/us/datasets/ds1");
        verify(dicomStores, times(1)).list("projects/p1/locations/us/datasets/ds2");
    }

    @Test
    @DisplayName("getAllDatasetsWithDicomStores() -> returns empty map when datasets list is null")
    void getAllDatasetsWithDicomStores_datasetsNull_returnsEmpty() throws Exception {
        when(datasets.list("projects/p1/locations/us")).thenReturn(datasetsListReq);
        when(datasetsListReq.execute()).thenReturn(new ListDatasetsResponse().setDatasets(null));

        Map<String, List<String>> result = service.getAllDatasetsWithDicomStores();

        assertThat(result).isEmpty();
        verify(dicomStores, never()).list(anyString());
    }

    @Test
    @DisplayName("getAllDatasetsWithDicomStores() -> dataset present with empty list when dicomStores is null")
    void getAllDatasetsWithDicomStores_dicomStoresNull_returnsEmptyList() throws Exception {
        Dataset ds1 = new Dataset().setName("projects/p1/locations/us/datasets/ds1");

        when(datasets.dicomStores()).thenReturn(dicomStores);
        when(datasets.list("projects/p1/locations/us")).thenReturn(datasetsListReq);
        when(datasetsListReq.execute()).thenReturn(new ListDatasetsResponse().setDatasets(List.of(ds1)));

        when(dicomStores.list("projects/p1/locations/us/datasets/ds1")).thenReturn(dicomStoresListReq);
        when(dicomStoresListReq.execute()).thenReturn(new ListDicomStoresResponse().setDicomStores(null));

        Map<String, List<String>> result = service.getAllDatasetsWithDicomStores();

        assertThat(result).containsKey("ds1");
        assertThat(result.get("ds1")).isEmpty();
    }

    @Test
    @DisplayName("getAllDatasetsWithDicomStores() -> returns empty map when list datasets throws IOException")
    void getAllDatasetsWithDicomStores_listDatasetsThrows_returnsEmpty() throws Exception {
        when(datasets.list("projects/p1/locations/us")).thenReturn(datasetsListReq);
        when(datasetsListReq.execute()).thenThrow(new IOException("boom"));

        Map<String, List<String>> result = service.getAllDatasetsWithDicomStores();

        assertThat(result).isEmpty();
        verify(dicomStores, never()).list(anyString());
    }

    @Test
    @DisplayName("getAllDatasetsWithDicomStores() -> stops and returns partial map when dicomStores list throws IOException")
    void getAllDatasetsWithDicomStores_dicomStoresThrows_returnsPartial() throws Exception {
        Dataset ds1 = new Dataset().setName("projects/p1/locations/us/datasets/ds1");
        Dataset ds2 = new Dataset().setName("projects/p1/locations/us/datasets/ds2");

        when(datasets.dicomStores()).thenReturn(dicomStores);
        when(datasets.list("projects/p1/locations/us")).thenReturn(datasetsListReq);
        when(datasetsListReq.execute()).thenReturn(new ListDatasetsResponse().setDatasets(List.of(ds1, ds2)));

        DicomStore s1 = new DicomStore().setName("projects/p1/locations/us/datasets/ds1/dicomStores/storeA");
        when(dicomStores.list("projects/p1/locations/us/datasets/ds1")).thenReturn(dicomStoresListReq);
        when(dicomStoresListReq.execute()).thenReturn(new ListDicomStoresResponse().setDicomStores(List.of(s1)));

        CloudHealthcare.Projects.Locations.Datasets.DicomStores.List ds2Req =
                mock(CloudHealthcare.Projects.Locations.Datasets.DicomStores.List.class);
        when(dicomStores.list("projects/p1/locations/us/datasets/ds2")).thenReturn(ds2Req);
        when(ds2Req.execute()).thenThrow(new IOException("dicom fail"));

        Map<String, List<String>> result = service.getAllDatasetsWithDicomStores();

        assertThat(result).containsKey("ds1");
        assertThat(result.get("ds1")).containsExactly("projects/p1/locations/us/datasets/ds1/dicomStores/storeA");
        assertThat(result).doesNotContainKey("ds2"); // because exception aborts loop due to try/catch scope
    }
}