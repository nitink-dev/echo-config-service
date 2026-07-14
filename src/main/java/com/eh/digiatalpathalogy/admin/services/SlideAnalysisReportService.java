package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.client.SlideAnalysisReportClient;
import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.constant.ConfigKeys;
import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.entity.SlideAnalysisReport;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisError;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisMessage;
import com.eh.digiatalpathalogy.admin.model.SlideAnalysisRequest;
import com.eh.digiatalpathalogy.admin.repository.SlideAnalysisReportRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Date;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.PATH_QA_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.ANALYSIS_ID_PREFIX;

@Service
@RefreshScope
public class SlideAnalysisReportService {

    private static final Logger log = LoggerFactory.getLogger(SlideAnalysisReportService.class);
    private static final String ANALYSIS_COMPLETE = "Completed";

    @Value("${path.qa-baseurl}")
    private String pathQABaseUrl;

    @Value("${path.retry-attempt}")
    private int retryAttempt;

    @Value("${path.duration}")
    private int duration;

    private final QaSlideService qaSlideService;
    private final SlideAnalysisReportClient slideAnalysisReportClient;
    private final RedisEntityStore redisStore;
    private final ConfigStore configStore;
    private final SlideAnalysisReportRepository slideAnalysisReportRepository;

    public SlideAnalysisReportService(QaSlideService qaSlideService, SlideAnalysisReportClient slideAnalysisReportClient, RedisEntityStore redisStore,
                                      ConfigStore configStore, SlideAnalysisReportRepository slideAnalysisReportRepository) {
        this.qaSlideService = qaSlideService;
        this.slideAnalysisReportClient = slideAnalysisReportClient;
        this.redisStore = redisStore;
        this.configStore = configStore;
        this.slideAnalysisReportRepository = slideAnalysisReportRepository;
    }

    public Mono<String> buildRequestUrl(String studyUID, String seriesUID) {
        return Mono.zip(getGcpBaseUrl(), getPathQaDicomStoreUrl())
                .map(tuple -> String.format("%s/%s/dicomWeb/studies/%s/series/%s",
                        tuple.getT1(), tuple.getT2(), studyUID, seriesUID))
                .doOnSuccess(url -> log.info("Built request URL: {}", url))
                .doOnError(e -> log.error("SlideAnalysisReportService :: buildRequestUrl ::  Failed to build request URL", e));
    }

    public Mono<SlideAnalysisReport> getAnalysisById(String analysisId) {
        log.info("Fetching analysis report by ID: {}", analysisId);
        String analysisKey = ANALYSIS_ID_PREFIX + analysisId;
        return redisStore.findByKeyWithFallback(analysisKey, () ->
                        slideAnalysisReportRepository.findByAnalysisId(analysisId)
                                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Slide Analysis Report not found with analysis ID: " + analysisId))), SlideAnalysisReport.class)
                .doOnSuccess(scanner -> log.info("Slide Analysis Report retrieved successfully: AnalysisID={}", analysisId))
                .doOnError(error -> log.error("Failed to retrieve slide analysis report with AnalysisID={}: {}", analysisId, error.getMessage(), error));

    }

    public Flux<SlideAnalysisReport> getReportByDeviceSerialNumber(String deviceSerialNumber) {
        log.info("Initiating retrieval of analysis reports for deviceSerialNumber ID: {}", deviceSerialNumber);
        return redisStore.findByPatternWithFallback(ANALYSIS_ID_PREFIX, report -> deviceSerialNumber.equals(report.getDeviceSerialNumber()),
                        () -> slideAnalysisReportRepository.findByDeviceSerialNumber(deviceSerialNumber),
                        SlideAnalysisReport::getAnalysisId, SlideAnalysisReport.class)
                .flatMapMany(Flux::fromIterable)
                .doOnSubscribe(sub -> log.debug("Started fetching analysis reports for deviceSerialNumber: {}", deviceSerialNumber))
                .doOnComplete(() -> log.info("Completed retrieval of analysis reports for deviceSerialNumber: {}", deviceSerialNumber))
                .doOnError(e -> log.error("Error occurred while retrieving analysis reports for deviceSerialNumber: {}: {}", deviceSerialNumber, e.getMessage(), e));
    }

    public Mono<String> getPathQaDicomStoreUrl() {
        return configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION);
    }

    public Mono<Void> processAnalysisRequest(SlideAnalysisMessage message) {
        log.info("Processing analysis request for barcodeValue: {}, studyUID: {}, seriesUID: {}",
                message.barcodeValue(), message.studyInstanceUid(), message.seriesInstanceUid());

        return getQaSlideDetails(message.barcodeValue())
                .flatMap(qaSlide ->
                        buildRequestUrl(message.studyInstanceUid(), message.seriesInstanceUid())
                                .flatMap(dicomSeriesPath -> {
                                    SlideAnalysisRequest request = new SlideAnalysisRequest(qaSlide.barcode(), qaSlide.activationCode(), dicomSeriesPath);
                                    return slideAnalysisReportClient.submitAnalysis(request)
                                            .flatMap(response -> {
                                                if (isFailed(response)) {
                                                    return handleFailedAnalysis(response, response.getAnalysisId());
                                                } else {
                                                    log.info("Analysis submitted successfully. Analysis ID: {}", response.getAnalysisId());
                                                    fetchAnalysisDetails(response.getAnalysisId(), message.deviceSerialNumber(), request.slideId()).subscribe();
                                                }
                                                return Mono.empty();
                                            });
                                })
                );
    }

    private Mono<QaSlide> getQaSlideDetails(String barcode) {
        log.info("Fetching QA slide details for barcodeValue: {}", barcode);
        return qaSlideService.getByBarcode(barcode);
    }

    public Mono<String> getGcpBaseUrl() {
        return configStore.get(ConfigKeys.DICOM_WEB_URL);
    }

    private Mono<Void> fetchAnalysisDetails(String analysisId, String deviceSerialNumber, String slideBarcode) {
        log.info("Fetching analysis details for analysis ID: {}", analysisId);

        return slideAnalysisReportClient.analysisDetails(analysisId)
                .flatMap(details -> {
                    if (isFailed(details)) {
                        return handleFailedAnalysis(details, analysisId);
                    }
                    return handleAnalysisDetails(details, analysisId, deviceSerialNumber, slideBarcode);
                })
                .retryWhen(
                        Retry.fixedDelay(retryAttempt, Duration.ofMinutes(duration))
                                .filter(throwable -> !(throwable instanceof IllegalStateException)) // don't retry on failed status
                                .doBeforeRetry(retry -> log.info("Retrying fetchAnalysisDetails for analysisId={} (attempt #{})",
                                        analysisId, retry.totalRetries() + 1))
                                .onRetryExhaustedThrow((spec, signal) ->
                                        new RuntimeException("Max retries exhausted for analysisId=" + analysisId))
                )
                .onErrorResume(e -> {
                    log.warn("Failed to fetch analysis details for analysisId={}: {}", analysisId, e.getMessage());
                    return Mono.empty();
                });
    }

    private boolean isFailed(SlideAnalysisReport details) {
        return details != null && "Failed".equalsIgnoreCase(details.getStatus());
    }

    private Mono<Void> handleAnalysisDetails(SlideAnalysisReport details, String analysisId,
                                             String deviceSerialNumber, String slideBarcode) {
        if (details == null) {
            log.warn("No details found for analysisId={}", analysisId);
            return Mono.error(new RuntimeException("No analysis details found"));
        }

        if (ANALYSIS_COMPLETE.equalsIgnoreCase(details.getStatus())) {
            return saveCompletedAnalysis(details, deviceSerialNumber, slideBarcode);
        }

        log.warn("Analysis not complete for ID: {}", analysisId);
        return Mono.error(new RuntimeException("Analysis not complete"));
    }


    private Mono<Void> saveCompletedAnalysis(SlideAnalysisReport details, String deviceSerialNumber, String slideBarcode) {
        details.setDeviceSerialNumber(deviceSerialNumber);
        details.setCreatedAt(new Date());
        details.setSlideBarcode(slideBarcode);
        return slideAnalysisReportRepository.save(details)
                .then(redisStore.deleteKeysByPattern(ANALYSIS_ID_PREFIX + "*"))
                .doOnSuccess(saved -> log.info("SlideAnalysisReport saved for analysisId={}", details.getAnalysisId()))
                .then();
    }


    private Mono<Void> handleFailedAnalysis(SlideAnalysisReport details, String analysisId) {
        SlideAnalysisError error = details.getError();

        log.info("Analysis failed for ID: {}. Error Code: {}, Message: {}, Details: {}",
                analysisId,
                error != null ? error.getCode() : "N/A",
                error != null ? error.getMessage() : "N/A",
                error != null ? error.getDetails() : "N/A"
        );

        return Mono.error(new IllegalStateException("Analysis failed for analysisId=" + analysisId));
    }

}
