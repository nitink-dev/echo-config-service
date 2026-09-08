package com.eh.digiatalpathalogy.admin.services;


import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.repository.SlideScannerRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.RESEARCH_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.testdata.SlideScannerTestData.scanner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlideScannerServiceTest {

    @Mock
    private RedisEntityStore redisStore;
    @Mock
    private DicomStoreService dicomStoreService;
    @Mock
    private ConfigStore configStore;
    @Mock
    private SlideScannerRepository slideScannerRepository;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private SlideScannerService service;


    @Test
    @DisplayName("getByDeviceSerialNumber() → item found via cache fallback")
    void getByDeviceSerialNumber_ok() {
        SlideScanner s = scanner("SS12118");

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(s));
        StepVerifier.create(service.getByDeviceSerialNumber("SS12118"))
                .expectNextMatches(sc -> sc.getDeviceSerialNumber().equals("SS12118"))
                .verifyComplete();
    }

    @Test
    @DisplayName("getByDeviceSerialNumber() → not found → ResourceNotFoundException")
    void getByDeviceSerialNumber_notFound() {

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Slide scanner not found with DeviceSerialNumber ID: Unavailable-Device-Serial-Number")));
        StepVerifier.create(service.getByDeviceSerialNumber("Unavailable-Device-Serial-Number"))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException
                        && ex.getMessage().contains("DeviceSerialNumber ID: Unavailable-Device-Serial-Number"))
                .verify();
    }

    @Test
    @DisplayName("create() → default research=false, connected=true, clears caches")
    void create_defaults_and_cache_clear() {

        SlideScanner input = scanner("SS12118");

        when(slideScannerRepository.save(any(SlideScanner.class))).thenReturn(Mono.just(input));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), isNull(), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.create(input))
                .expectNextMatches(sc ->
                        Boolean.FALSE.equals(sc.getResearch()) &&
                                Boolean.TRUE.equals(sc.getConnected()) &&
                                "SS12118".equals(sc.getDeviceSerialNumber()))
                .verifyComplete();

        verify(notificationService).notifyEntityChange(eq("scanner"), isNull(), any(SlideScanner.class));
    }

    @Test
    @DisplayName("create() → DuplicateKeyException → HttpRequestException CONFLICT")
    void create_duplicate_conflict() {
        SlideScanner input = scanner("SS12118");
        when(slideScannerRepository.save(any(SlideScanner.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("dup")));

        StepVerifier.create(service.create(input))
                .expectErrorMatches(ex ->
                        ex instanceof HttpRequestException &&
                                ((HttpRequestException) ex).getStatus() == HttpStatus.CONFLICT &&
                                ex.getMessage().contains("already exists"))
                .verify();

    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → research=true & dicomStore present in payload → clears dept/dicomStore; returns refreshed entity")
    void update_research_true_clears_fields_and_refreshes() {
        SlideScanner patch = new SlideScanner();
        patch.setResearch(Boolean.TRUE);
        patch.setDicomStore("some-incoming-will-be-ignored"); // as per service logic

        SlideScanner updatedInDb = scanner("SS12118");
        updatedInDb.setResearch(Boolean.TRUE);

        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true))).thenReturn(Mono.just(updatedInDb));
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class))).thenReturn(Mono.just(updatedInDb));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("S1", patch))
                .expectNextMatches(sc ->
                        sc.getDeviceSerialNumber().equals("SS12118") &&
                                Boolean.TRUE.equals(sc.getResearch()))
                .verifyComplete();

        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                payload.getDepartment() == null && payload.getDicomStore() == null), eq(true));
        verify(notificationService).notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → partial patch with only one field set → other fields preserved from existing record")
    void update_partialPatch_preservesOmittedFields() {
        SlideScanner existing = scanner("SS12118");

        SlideScanner patch = new SlideScanner();
        patch.setName("Updated-Name-Only");

        SlideScanner updatedInDb = scanner("SS12118");
        updatedInDb.setName("Updated-Name-Only");

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class))).thenReturn(Mono.just(existing));
        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true))).thenReturn(Mono.just(updatedInDb));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", patch))
                .expectNextMatches(sc -> "Updated-Name-Only".equals(sc.getName()))
                .verifyComplete();

        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                "Updated-Name-Only".equals(payload.getName())
                        && "GT450DX".equals(payload.getModel())
                        && "Evanston".equals(payload.getLocation())
                        && "digital-pathology-dataset".equals(payload.getDepartment())
                        && "projects/p1/locations/l1/datasets/d1/dicomStores/store1".equals(payload.getDicomStore())
                        && "SVS_STORE_SCP".equals(payload.getAeTitle())
                        && "1010".equals(payload.getPort())
                        && "Evanston Hospital".equals(payload.getHospitalName())
                        && "10.0.0.10".equals(payload.getIpAddress())
                        && "Acme".equals(payload.getVendor())
                        && Boolean.FALSE.equals(payload.getResearch())
                        && Boolean.TRUE.equals(payload.getConnected())
                        && "SRORESCP".equals(payload.getRemoteAeTitle())
                        && "171.33.43.10".equals(payload.getRemoteHost())
                        && Integer.valueOf(9999).equals(payload.getRemotePort())
                        && "C-STORE".equals(payload.getStorageStrategy())
        ), eq(true));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → explicit blank on a non-mandatory String field → clears it instead of keeping the old value")
    void update_explicitBlankStringField_clearsField() {
        SlideScanner existing = scanner("SS12118");

        SlideScanner patch = new SlideScanner();
        patch.setRemoteAeTitle("");

        SlideScanner updatedInDb = scanner("SS12118");
        updatedInDb.setRemoteAeTitle(null);

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class))).thenReturn(Mono.just(existing));
        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true))).thenReturn(Mono.just(updatedInDb));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", patch, Set.of("remoteAeTitle")))
                .expectNextCount(1)
                .verifyComplete();

        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                payload.getRemoteAeTitle() == null
                        && "171.33.43.10".equals(payload.getRemoteHost()) // untouched fields still preserved
        ), eq(true));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → explicit blank on remotePort (present in request, coerced to null) → clears it instead of keeping the old value")
    void update_explicitBlankRemotePort_clearsField() {
        SlideScanner existing = scanner("SS12118");

        SlideScanner patch = new SlideScanner(); // remotePort left null, as Jackson would coerce "" -> null
        patch.setModel("Updated-Model");

        SlideScanner updatedInDb = scanner("SS12118");
        updatedInDb.setRemotePort(null);

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class))).thenReturn(Mono.just(existing));
        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true))).thenReturn(Mono.just(updatedInDb));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", patch, Set.of("model", "remotePort")))
                .expectNextCount(1)
                .verifyComplete();

        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                payload.getRemotePort() == null
                        && "Updated-Model".equals(payload.getModel())
                        && "SRORESCP".equals(payload.getRemoteAeTitle()) // untouched fields still preserved
        ), eq(true));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → blank deviceSerialNumber → BAD_REQUEST")
    void update_blankDeviceId_badRequest() {
        SlideScanner patch = new SlideScanner();
        StepVerifier.create(service.updateByDeviceSerialNumber("  ", patch))
                .expectErrorMatches(ex -> ex instanceof HttpRequestException &&
                        ((HttpRequestException) ex).getStatus() == HttpStatus.BAD_REQUEST &&
                        ex.getMessage().contains("must not be blank"))
                .verify();
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → null body → BAD_REQUEST")
    void update_nullBody_badRequest() {
        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", null))
                .expectErrorMatches(ex -> ex instanceof HttpRequestException &&
                        ((HttpRequestException) ex).getStatus() == HttpStatus.BAD_REQUEST &&
                        ex.getMessage().contains("must not be null"))
                .verify();
    }

    @Test
    @DisplayName("deleteByDeviceSerialNumber() → count=0 → ResourceNotFoundException")
    void delete_notFound() {
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(scanner("unavailable-device-serial-number")));
        when(slideScannerRepository.deleteByDeviceSerialNumber("unavailable-device-serial-number"))
                .thenReturn(Mono.just(0L));
        StepVerifier.create(service.deleteByDeviceSerialNumber("unavailable-device-serial-number"))
                .expectErrorMatches(ex -> ex instanceof ResourceNotFoundException &&
                        ex.getMessage().contains("Slide Scanner not found with Device :serial Number unavailable-device-serial-number"))
                .verify();

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("deleteByDeviceSerialNumber() → count>0 → true, clears caches & notifies with the deleted scanner's name")
    void delete_ok() {
        SlideScanner deleted = scanner("SS12118");

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(deleted));
        when(slideScannerRepository.deleteByDeviceSerialNumber("SS12118")).thenReturn(Mono.just(1L));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), isNull())).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteByDeviceSerialNumber("SS12118"))
                .expectNext(true)
                .verifyComplete();

        verify(notificationService).notifyEntityChange(eq("scanner"), argThat((SlideScanner old) -> old.getName().equals(deleted.getName())), isNull());
    }

    @Test
    @DisplayName("fetchDatasetsWithDicomStoresByResearch(true) → returns map with research dataset only")
    void datasets_research_true() {
        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION))
                .thenReturn(Mono.just("projects/p/locations/l/datasets/dResearch/dicomStores/storeX"));
        StepVerifier.create(service.fetchDatasetsWithDicomStoresByResearch(true))
                .expectNextMatches(map ->
                        map.containsKey("dResearch") &&
                                map.get("dResearch").size() == 1 &&
                                map.get("dResearch").get(0).endsWith("/storeX"))
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchDatasetsWithDicomStoresByResearch(false) → returns full map from dicomStoreService")
    void datasets_research_false() {

        String researchUrl = "projects/test-project/locations/us-central1/datasets/digital-pathology-dataset/dicomStores/dp-academic-dicom-store";
        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION)).thenReturn(Mono.just(researchUrl));
        when(dicomStoreService.getAllDatasetsWithDicomStores())
                .thenReturn(Map.of(
                        "d1", List.of(".../dicomStores/s1",researchUrl),
                        "d2", List.of(".../dicomStores/s2")
                ));

        StepVerifier.create(service.fetchDatasetsWithDicomStoresByResearch(false))
                .expectNextMatches(map -> map.containsKey("d1") && map.containsKey("d2"))
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchDatasetsWithDicomStoresByResearch(false) → excludes research URL and keeps other stores")
    void datasets_research_false_excludes_research() {

        String researchUrl =
                "projects/test-project/locations/us-central1/datasets/digital-pathology-dataset/dicomStores/dp-academic-dicom-store";

        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION)).thenReturn(Mono.just(researchUrl));
        when(dicomStoreService.getAllDatasetsWithDicomStores())
                .thenReturn(Map.of(
                        "digital-pathology-dataset", List.of(".../dicomStores/s1", researchUrl),
                        "department-two", List.of(".../dicomStores/s2")
                ));

        StepVerifier.create(service.fetchDatasetsWithDicomStoresByResearch(false))
                .assertNext(map -> {

                    assertTrue(map.containsKey("digital-pathology-dataset") && map.containsKey("department-two"));
                    boolean anyResearchPresent = map.values().stream().flatMap(List::stream).anyMatch(researchUrl::equals);

                    assertFalse(anyResearchPresent, "Research URL must be excluded when flag=false");
                    assertEquals(List.of(".../dicomStores/s1"), map.get("digital-pathology-dataset"),
                            "digital-pathology-dataset should only contain non-research stores");
                    assertEquals(List.of(".../dicomStores/s2"), map.get("department-two"),
                            "department-two should remain unchanged");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("extractDatasetId() → throws on invalid path")
    void extractDatasetId_invalid() {

        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION)).thenReturn(Mono.just("invalid/path"));
        StepVerifier.create(service.fetchDatasetsWithDicomStoresByResearch(true))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("list() → uses DB fallback (repository.findAll) and keeps dicomStore when configStore errors")
    void list_dbFallback_and_configError_keepsOriginalDicomStore() {
        SlideScanner r1 = scanner("SS12118");
        r1.setResearch(Boolean.TRUE);

        when(slideScannerRepository.findAll()).thenReturn(Flux.just(r1));

        // force redisStore to execute the dbFallback supplier so repository.findAll is covered
        doAnswer(inv -> {
            Supplier<Flux<SlideScanner>> dbFallback = inv.getArgument(2);
            return dbFallback.get().collectList();
        }).when(redisStore).findByPatternWithFallback(anyString(), any(), any(), any(), eq(SlideScanner.class));

        StepVerifier.create(service.list())
                .expectNextMatches(sc ->
                        "SS12118".equals(sc.getDeviceSerialNumber()) &&
                                "projects/p1/locations/l1/datasets/d1/dicomStores/store1".equals(sc.getDicomStore()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getByDeviceSerialNumber() → research=true but configStore errors → applyResearchDicomUrl onErrorResume keeps original")
    void getByDeviceSerialNumber_research_configError_keepsOriginal() {
        SlideScanner s = scanner("SS12118");
        s.setResearch(Boolean.TRUE);
        s.setDicomStore("projects/p/locations/l/datasets/d/dicomStores/original");

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(s));
        StepVerifier.create(service.getByDeviceSerialNumber("SS12118"))
                .expectNextMatches(sc ->
                        "SS12118".equals(sc.getDeviceSerialNumber()) &&
                                "projects/p/locations/l/datasets/d/dicomStores/original".equals(sc.getDicomStore()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getByDeviceSerialNumber() → redis executes fallback supplier and repository returns entity")
    void getByDeviceSerialNumber_executesFallbackSupplier_success() {
        SlideScanner s = scanner("SS12118");

        when(slideScannerRepository.findByDeviceSerialNumber("SS12118"))
                .thenReturn(Mono.just(s));

        // Make redisStore call the fallback supplier passed by the service
        doAnswer(inv -> {
            Supplier<Mono<SlideScanner>> fallback = inv.getArgument(1);
            return fallback.get();
        }).when(redisStore).findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class));
        StepVerifier.create(service.getByDeviceSerialNumber("SS12118"))
                .expectNextMatches(sc -> "SS12118".equals(sc.getDeviceSerialNumber()))
                .verifyComplete();

        verify(slideScannerRepository).findByDeviceSerialNumber("SS12118");
    }

    @Test
    @DisplayName("getByDeviceSerialNumber() → fallback repository empty → switchIfEmpty emits ResourceNotFoundException")
    void getByDeviceSerialNumber_fallbackRepositoryEmpty_notFound() {
        when(slideScannerRepository.findByDeviceSerialNumber("unavailable-device-serial-number"))
                .thenReturn(Mono.empty());

        doAnswer(inv -> {
            Supplier<Mono<SlideScanner>> fallback = inv.getArgument(1);
            return fallback.get();
        }).when(redisStore).findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class));

        StepVerifier.create(service.getByDeviceSerialNumber("unavailable-device-serial-number"))
                .expectErrorMatches(ex ->
                        ex instanceof ResourceNotFoundException &&
                                ex.getMessage().contains("DeviceSerialNumber ID: unavailable-device-serial-number"))
                .verify();

        verify(slideScannerRepository).findByDeviceSerialNumber("unavailable-device-serial-number");
    }

    @Test
    @DisplayName("create() → keeps provided research/connected, sets id=null and deviceId=deviceSerialNumber")
    void create_keepsProvidedFlags_and_setsDeviceId() {
        SlideScanner input = new SlideScanner();
        input.setDeviceSerialNumber("new-device-serial-number");
        input.setResearch(Boolean.TRUE);
        input.setConnected(Boolean.FALSE);

        SlideScanner saved = scanner("new-device-serial-number");
        saved.setResearch(Boolean.TRUE);
        saved.setConnected(Boolean.FALSE);

        when(slideScannerRepository.save(any(SlideScanner.class))).thenReturn(Mono.just(saved));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), isNull(), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.create(input))
                .expectNextMatches(sc -> "new-device-serial-number".equals(sc.getDeviceSerialNumber()) && Boolean.TRUE.equals(sc.getResearch()) && Boolean.FALSE.equals(sc.getConnected()))
                .verifyComplete();

        verify(slideScannerRepository).save(argThat(arg ->
                arg.getId() == null && "new-device-serial-number".equals(arg.getDeviceId()) && Boolean.TRUE.equals(arg.getResearch()) && Boolean.FALSE.equals(arg.getConnected())
        ));
        verify(notificationService).notifyEntityChange(eq("scanner"), isNull(), any(SlideScanner.class));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → findAndModify empty → ResourceNotFoundException")
    void update_findAndModifyEmpty_notFound() {
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(scanner("unavailable-device-serial-number")));
        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("unavailable-device-serial-number", new SlideScanner()))
                .expectError(ResourceNotFoundException.class)
                .verify();

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → incomingResearch=false returns updated directly, clears caches & notifies with old+new")
    void update_nonResearch_returnsUpdated_and_invalidatesCache() {
        SlideScanner patch = new SlideScanner();
        patch.setResearch(Boolean.FALSE);
        patch.setDepartment("DeptX");
        patch.setDicomStore("projects/p/locations/l/datasets/d/dicomStores/storeX");

        SlideScanner updated = scanner("SS12118");
        updated.setResearch(Boolean.FALSE);

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class)))
                .thenReturn(Mono.just(scanner("SS12118")));
        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true)))
                .thenReturn(Mono.just(updated));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", patch))
                .expectNextMatches(sc -> "SS12118".equals(sc.getDeviceSerialNumber()) && Boolean.FALSE.equals(sc.getResearch()))
                .verifyComplete();

        // for non-research, service should not refetch after the update -> only the initial oldData lookup hits findByKeyWithFallback
        verify(redisStore, times(1)).findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class));
        verify(redisStore, times(2)).deleteKeysByPattern(anyString());

        // ensure service nullified id + deviceSerialNumber in payload before passing to repo
        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                payload.getId() == null && payload.getDeviceSerialNumber() == null
        ), eq(true));
        verify(notificationService).notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class));
    }

    @Test
    @DisplayName("updateByDeviceSerialNumber() → research=true but incoming dicomStore blank → does NOT clear dept/dicomStore")
    void update_research_true_butBlankDicomStore_doesNotClearFields() {
        SlideScanner patch = new SlideScanner();
        patch.setResearch(Boolean.TRUE);
        patch.setDepartment("DeptY");
        patch.setDicomStore("   "); // blank => StringUtils.hasText false => should NOT clear dept/dicomStore

        SlideScanner updatedInDb = scanner("SS12118");
        updatedInDb.setResearch(Boolean.TRUE);

        when(slideScannerRepository.findAndModify(any(Query.class), any(SlideScanner.class), eq(true)))
                .thenReturn(Mono.just(updatedInDb));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());

        // incomingResearch=true triggers refetch, so stub it
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideScanner.class))).thenReturn(Mono.just(updatedInDb));
        when(notificationService.notifyEntityChange(eq("scanner"), any(SlideScanner.class), any(SlideScanner.class))).thenReturn(Mono.empty());
        StepVerifier.create(service.updateByDeviceSerialNumber("SS12118", patch))
                .expectNextMatches(sc -> "SS12118".equals(sc.getDeviceSerialNumber()) && Boolean.TRUE.equals(sc.getResearch()))
                .verifyComplete();

        verify(slideScannerRepository).findAndModify(any(Query.class), argThat(payload ->
                "DeptY".equals(payload.getDepartment()) && Objects.nonNull(payload.getDicomStore()) && payload.getDicomStore().isBlank()
        ), eq(true));
    }

    @Test
    @DisplayName("fetchDatasetsWithDicomStores(true) → propagates error (covers doOnError in wrapper)")
    void fetchDatasetsWithDicomStores_wrapper_errorPath() {
        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION))
                .thenReturn(Mono.error(new RuntimeException("config unavailable")));

        StepVerifier.create(service.fetchDatasetsWithDicomStores(true))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("extractDatasetId() → blank dicomStorePath throws InternalServerException")
    void extractDatasetId_blank_throwsInternalServerException() {
        when(configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION))
                .thenReturn(Mono.just("   "));

        StepVerifier.create(service.fetchDatasetsWithDicomStoresByResearch(true))
                .expectError(InternalServerException.class)
                .verify();
    }
}