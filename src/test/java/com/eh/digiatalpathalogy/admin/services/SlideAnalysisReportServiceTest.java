package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.SlideAnalysisReportClient;
import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.constant.ConfigKeys;
import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.HospitalMetadataDTO;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisError;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisRequest;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisResult;
import com.eh.digiatalpathalogy.admin.repository.SlideAnalysisReportRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.function.Supplier;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.PATH_QA_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.ANALYSIS_ID_PREFIX;
import static com.eh.digiatalpathalogy.admin.testdata.SlideAnalysisReportTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlideAnalysisReportServiceTest {

    @Mock
    private QaSlideService qaSlideService;
    @Mock
    private SlideAnalysisReportClient slideAnalysisReportClient;
    @Mock
    private RedisEntityStore redisStore;
    @Mock
    private ConfigStore configStore;
    @Mock
    private SlideAnalysisReportRepository slideAnalysisReportRepository;

    @InjectMocks
    private SlideAnalysisReportService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "retryAttempt", 1);
        ReflectionTestUtils.setField(service, "duration", 0); // minutes -> 0 to avoid waiting
        ReflectionTestUtils.setField(service, "pathQABaseUrl", "unused");
    }

    @Test
    @DisplayName("buildRequestUrl() -> builds expected dicomWeb URL")
    void buildRequestUrl_buildsExpectedUrl() {

        given(configStore.get(ConfigKeys.DICOM_WEB_URL)).willReturn(Mono.just(TEST_DICOM_WEB_URL));
        given(configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION)).willReturn(Mono.just(TEST_PATH_QA_DICOM_STORE));

        Mono<String> urlMono = service.buildRequestUrl("study-1.2.23.1", "series-1.44.6.90");

        StepVerifier.create(urlMono)
                .assertNext(url ->
                        assertThat(url).isEqualTo("https://healthcare.googleapis.com/v1/ projects/prj-d-path-integration-cs1h/locations/us-central1/datasets/digital-pathology-dataset/dicomStores/digital-pathology-pathqa-dicomstore/dicomWeb/studies/study-1.2.23.1/series/series-1.44.6.90")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("getAnalysisById() -> returns report from Database")
    void getAnalysisById_returnsFromDB() {

        String analysisId = "report-1";
        SlideAnalysisReport report = analysisReport("report-1");

        given(slideAnalysisReportRepository.findByAnalysisId(analysisId)).willReturn(Mono.just(report));
        given(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideAnalysisReport.class)))
                .willAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Mono<SlideAnalysisReport>> fallback =
                            (Supplier<Mono<SlideAnalysisReport>>) inv.getArgument(1);
                    return fallback.get();
                });

        StepVerifier.create(service.getAnalysisById(analysisId))
                .assertNext(r -> assertThat(r.getAnalysisId()).isEqualTo("report-1"))
                .verifyComplete();

        verify(slideAnalysisReportRepository, times(1)).findByAnalysisId(analysisId);
    }

    @Test
    @DisplayName("getAnalysisById() -> returns report from redisStore")
    void getAnalysisById_returnsFromRedisStore() {

        String analysisId = "report-1";
        SlideAnalysisReport report = analysisReport("report-1");

        given(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideAnalysisReport.class)))
                .willReturn(Mono.just(report));

        StepVerifier.create(service.getAnalysisById(analysisId))
                .assertNext(r -> assertThat(r.getAnalysisId()).isEqualTo(analysisId))
                .verifyComplete();
    }

    @Test
    @DisplayName("getAnalysisById() -> emits ResourceNotFoundException when fallback empty")
    void getAnalysisById_whenNotFound_emitsResourceNotFound() {

        String missingAnalysisId = "report-404";

        given(redisStore.findByKeyWithFallback(anyString(), any(), eq(SlideAnalysisReport.class)))
                .willReturn(Mono.error(new ResourceNotFoundException("Slide Analysis Report not found with analysis ID: " + missingAnalysisId)));

        StepVerifier.create(service.getAnalysisById(missingAnalysisId))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(ResourceNotFoundException.class);
                    assertThat(ex.getMessage()).isEqualTo("Slide Analysis Report not found with analysis ID: " + missingAnalysisId);
                })
                .verify();
    }

    @Test
    @DisplayName("getReportByDeviceSerialNumber() -> returns reports list from redis pattern lookup")
    void getReportByDeviceSerialNumber_returnsFlux() {

        String device = "DEV-1";
        SlideAnalysisReport report1 = analysisReport("report-1");
        SlideAnalysisReport report2 = analysisReport("report-2");

        SlideAnalysisResult result = new SlideAnalysisResult();
        result.setPdfReportUrl("http://path-qa-service/report.pdf");
        result.setCsvDataFileUrl("http://path-qa-service/data.csv");

        report1.setResult(result);
        report2.setResult(result);


        given(redisStore.findByPatternWithFallback(anyString(), any(), any(), any(), eq(SlideAnalysisReport.class)))
                .willReturn(Mono.just(List.of(report1, report2)));

        StepVerifier.create(service.getReportByDeviceSerialNumber(device))
                .expectNextMatches(x -> "report-1".equals(x.getAnalysisId()) && "http://path-qa-service/report.pdf".equals(x.getResult().getPdfReportUrl()))
                .expectNextMatches(x -> "report-2".equals(x.getAnalysisId()) && "http://path-qa-service/data.csv".equals(x.getResult().getCsvDataFileUrl()))
                .verifyComplete();
    }

    @Test
    @DisplayName("processAnalysisRequest() -> emits IllegalStateException when submitAnalysis returns Failed")
    void processAnalysisRequest_whenSubmitFailed_emitsIllegalState() {

        QaSlide qaSlide = mock(QaSlide.class);
        given(qaSlide.barcode()).willReturn(QA_SLIDE_BARCODE);
        given(qaSlide.activationCode()).willReturn("test-activation-code");

        given(qaSlideService.getByBarcode(QA_SLIDE_BARCODE)).willReturn(Mono.just(qaSlide));
        given(configStore.get(ConfigKeys.DICOM_WEB_URL)).willReturn(Mono.just(TEST_DICOM_WEB_URL));
        given(configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION)).willReturn(Mono.just(TEST_PATH_QA_DICOM_STORE));

        SlideAnalysisReport failed = new SlideAnalysisReport();
        failed.setAnalysisId("AN-1");
        failed.setStatus("Failed");

        given(slideAnalysisReportClient.submitAnalysis(any(SlideAnalysisRequest.class))).willReturn(Mono.just(failed));

        StepVerifier.create(service.processAnalysisRequest(kafkaMessage))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(IllegalStateException.class);
                    assertThat(ex.getMessage()).isEqualTo("Analysis failed for analysisId=AN-1");
                })
                .verify();

        verify(slideAnalysisReportClient, never()).analysisDetails(anyString());
        verify(slideAnalysisReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("processAnalysisRequest() -> when Completed details returned, saves report and clears redis pattern")
    void processAnalysisRequest_whenCompleted_savesAndClearsRedis() {

        QaSlide qaSlide = mock(QaSlide.class);
        given(qaSlide.barcode()).willReturn(QA_SLIDE_BARCODE);
        given(qaSlide.activationCode()).willReturn("ACT-9");

        given(qaSlideService.getByBarcode(QA_SLIDE_BARCODE)).willReturn(Mono.just(qaSlide));
        given(configStore.get(ConfigKeys.DICOM_WEB_URL)).willReturn(Mono.just(TEST_DICOM_WEB_URL));
        given(configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION)).willReturn(Mono.just(TEST_PATH_QA_DICOM_STORE));

        SlideAnalysisReport submitResp = new SlideAnalysisReport();
        submitResp.setAnalysisId("AN-9");
        submitResp.setStatus("Submitted");

        SlideAnalysisReport completed = new SlideAnalysisReport();
        completed.setAnalysisId("AN-9");
        completed.setStatus("Completed");

        given(slideAnalysisReportClient.submitAnalysis(any(SlideAnalysisRequest.class))).willReturn(Mono.just(submitResp));
        given(slideAnalysisReportClient.analysisDetails("AN-9")).willReturn(Mono.just(completed));
        given(slideAnalysisReportRepository.save(any(SlideAnalysisReport.class)))
                .willAnswer(inv -> {
                    SlideAnalysisReport saved = inv.getArgument(0);

                    assertThat(saved.getDeviceSerialNumber()).isEqualTo(DEVICE_SERIAL_NUMBER);
                    assertThat(saved.getSlideBarcode()).isEqualTo(QA_SLIDE_BARCODE);
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getAnalysisId()).isEqualTo("AN-9");

                    return Mono.just(saved);
                });
        given(redisStore.deleteKeysByPattern(anyString())).willReturn(Mono.empty());

        StepVerifier.create(service.processAnalysisRequest(kafkaMessage)).verifyComplete();

        verify(slideAnalysisReportClient, timeout(1000)).analysisDetails("AN-9");
        verify(slideAnalysisReportRepository, timeout(1000)).save(any(SlideAnalysisReport.class));
        verify(redisStore, timeout(1000)).deleteKeysByPattern(startsWith(ANALYSIS_ID_PREFIX));
    }


    @Test
    @DisplayName("SlideAnalysisError → getters and setters work correctly")
    void testGettersAndSetters() {
        SlideAnalysisError error = new SlideAnalysisError();

        error.setCode("E100");
        error.setMessage("Processing failed");
        error.setDetails("Invalid slide format");

        assertThat(error.getCode()).isEqualTo("E100");
        assertThat(error.getMessage()).isEqualTo("Processing failed");
        assertThat(error.getDetails()).isEqualTo("Invalid slide format");
    }

    @Test
    @DisplayName("Getter and Setter test for HospitalMetadataDTO")
    void getterSetterTest() {

        HospitalMetadataDTO dto = new HospitalMetadataDTO();

        List<String> locations = List.of("Endeavor Hospital", "Endeavor Medical Center");
        List<String> names = List.of("Addison", "Bolingbrook");
        dto.setLocations(locations);
        dto.setNames(names);
        assertEquals(locations, dto.getLocations());
        assertEquals(names, dto.getNames());
    }

    @Test
    @DisplayName("All-args constructor should set fields correctly")
    void allArgsConstructorTest() {

        List<String> locations = List.of("Endeavor Hospital", "Endeavor Medical Center");
        List<String> names = List.of("Addison", "Bolingbrook");

        HospitalMetadataDTO dto = new HospitalMetadataDTO(locations, names);

        assertEquals(locations, dto.getLocations());
        assertEquals(names, dto.getNames());
    }


}
