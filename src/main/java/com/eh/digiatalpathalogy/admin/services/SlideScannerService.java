package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.repository.SlideScannerRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.RESEARCH_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.DICOM_RECEIVER_SCANNER_DEVICE_PREFIX;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SCANNER_DEVICE_PREFIX;

@Service
public class SlideScannerService {

    private static final Logger log = LoggerFactory.getLogger(SlideScannerService.class);
    private static final String ERROR_MSG = "Slide Scanner not found with Device :serial Number ";

    private final RedisEntityStore redisStore;
    private final DicomStoreService dicomStoreService;
    private final ConfigStore configStore;
    private final SlideScannerRepository slideScannerRepository;
    private final NotificationService notificationService;


    public SlideScannerService(RedisEntityStore redisStore, DicomStoreService dicomStoreService, ConfigStore configStore, SlideScannerRepository slideScannerRepository, NotificationService notificationService) {
        this.redisStore = redisStore;
        this.dicomStoreService = dicomStoreService;
        this.configStore = configStore;
        this.slideScannerRepository = slideScannerRepository;
        this.notificationService = notificationService;

    }

    public Flux<SlideScanner> list() {

        return redisStore.findByPatternWithFallback(SCANNER_DEVICE_PREFIX + ":all:", slideScanner -> true,
                        slideScannerRepository::findAll,
                        SlideScanner::getDeviceSerialNumber, SlideScanner.class)
                .flatMapMany(Flux::fromIterable)
                .doOnSubscribe(sub -> log.debug("Starting retrieval of all slide scanners"))
                .doOnComplete(() -> log.debug("Completed retrieval of all slide scanners"));
    }

    public Mono<SlideScanner> getByDeviceSerialNumber(String deviceSerialNumber) {
        String deviceKey = SCANNER_DEVICE_PREFIX + deviceSerialNumber;
        return redisStore.findByKeyWithFallback(deviceKey, () -> findByDeviceSerialNumber(deviceSerialNumber)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Slide scanner not found with DeviceSerialNumber ID: " + deviceSerialNumber))), SlideScanner.class)
                .doOnSuccess(scanner -> log.info("Slide scanner retrieved successfully: DeviceSerialNumber={}", deviceSerialNumber))
                .doOnError(error -> log.error("Failed to retrieve slide scanner with DeviceSerialNumber={}: {}", deviceSerialNumber, error.getMessage(), error));
    }

    public Mono<SlideScanner> create(SlideScanner slideScanner) {

        if (Objects.isNull(slideScanner.getResearch())) {
            slideScanner.setResearch(Boolean.FALSE);
        }
        if (Objects.isNull(slideScanner.getConnected())) {
            slideScanner.setConnected(Boolean.TRUE);
        }
        if (Boolean.FALSE == slideScanner.getResearch() && !StringUtils.hasText(slideScanner.getDicomStore())) {
            return Mono.error(new ServerWebInputException("dicomStore is mandatory when research=false"));
        }

        slideScanner.setId(null);
        slideScanner.setDeviceId(slideScanner.getDeviceSerialNumber());

        return slideScannerRepository.save(slideScanner)
                .flatMap(saved -> redisStore.deleteKeysByPattern(SCANNER_DEVICE_PREFIX + "*")
                        .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_SCANNER_DEVICE_PREFIX + "*"))
                        .then(notificationService.notifyEntityChange("scanner", null, saved))
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Slide scanner created: DeviceSerialNumber={}", saved.getDeviceSerialNumber()))
                .onErrorMap(DuplicateKeyException.class, ex -> {
                    log.warn("Duplicate entry: DeviceSerialNumber={}", slideScanner.getDeviceSerialNumber(), ex);
                    return new HttpRequestException(HttpStatus.CONFLICT, "Slide scanner with the same Device SerialNumber or ID already exists.");
                })
                .doOnError(error -> log.error("Slide scanner creation failed: {}", error.getMessage(), error));
    }

    public Mono<SlideScanner> updateByDeviceSerialNumber(String deviceSerialNumber, SlideScanner slideScanner) {

        if (!StringUtils.hasText(deviceSerialNumber)) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "DeviceSerialNumber must not be blank."));
        }
        if (slideScanner == null) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Update payload must not be null."));
        }
        final boolean incomingResearch = Boolean.TRUE.equals(slideScanner.getResearch());
        final String incomingDicomStore = slideScanner.getDicomStore();
        slideScanner.setId(null);
        slideScanner.setDeviceSerialNumber(null);

        if (incomingResearch && StringUtils.hasText(incomingDicomStore)) {
            slideScanner.setDepartment(null);
            slideScanner.setDicomStore(null);
        }

        Query query = buildDeviceSerialNumberQuery(deviceSerialNumber);
        return getByDeviceSerialNumber(deviceSerialNumber)
                .flatMap(oldData -> slideScannerRepository.findAndModify(query, slideScanner, true)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Slide scanner not found with DeviceID: " + deviceSerialNumber)))
                        .flatMap(updated -> {
                            Mono<Void> invalidateCache = redisStore.deleteKeysByPattern(SCANNER_DEVICE_PREFIX + "*")
                                    .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_SCANNER_DEVICE_PREFIX + "*"))
                                    .then();
                            Mono<SlideScanner> result = incomingResearch ? getByDeviceSerialNumber(deviceSerialNumber) : Mono.just(updated);
                            return Mono.whenDelayError(invalidateCache)
                                    .then(result)
                                    .flatMap(finalResult -> notificationService.notifyEntityChange("scanner", oldData, finalResult)
                                            .thenReturn(finalResult));
                        }))
                .doOnSuccess(updated -> log.info("Slide scanner updated successfully: DeviceSerialNumber={}", updated.getDeviceSerialNumber()))
                .doOnError(error -> log.error("Failed to update slide scanner with DeviceSerialNumber={}: {}", deviceSerialNumber, error.getMessage(), error));
    }


    private Query buildDeviceSerialNumberQuery(String deviceSerialNumber) {
        return Query.query(Criteria.where("deviceSerialNumber").is(deviceSerialNumber));
    }

    private Mono<String> researchDicomUrlCached() {
        return getResearchDicomStoreUrl()
                .filter(StringUtils::hasText)
                .doOnError(ex -> log.warn("Failed to resolve research DICOM store URL: {}", ex.toString()))
                .onErrorResume(ex -> Mono.empty())
                .cache();
    }

    private Mono<SlideScanner> findByDeviceSerialNumber(String deviceSerialNumber) {
        return slideScannerRepository.findByDeviceSerialNumber(deviceSerialNumber);
    }

    public Mono<Map<String, List<String>>> fetchDatasetsWithDicomStores(Boolean isResearch) {
        log.info("Fetching datasets with DICOM stores");
        return fetchDatasetsWithDicomStoresByResearch(isResearch)
                .doOnError(error -> log.error("Error fetching datasets with DICOM stores: {}", error.getMessage(), error));
    }

    public Mono<Map<String, List<String>>> fetchDatasetsWithDicomStoresByResearch(Boolean isResearch) {

        return Mono.defer(() -> {
            if (Boolean.TRUE.equals(isResearch)) {
                return getResearchDicomStoreUrl()
                        .map(url -> Map.of(extractDatasetId(url), List.of(url)));
            }
            return researchDicomUrlCached()
                    .flatMap(researchUrl -> Mono.fromSupplier(() -> {
                        Map<String, List<String>> all = new HashMap<>(dicomStoreService.getAllDatasetsWithDicomStores());
                        return excludeResearchUrl(all, researchUrl);
                    }))
                    .switchIfEmpty(Mono.fromSupplier(dicomStoreService::getAllDatasetsWithDicomStores));
        });

    }

    public Mono<Boolean> deleteByDeviceSerialNumber(String deviceSerialNumber) {

        return getByDeviceSerialNumber(deviceSerialNumber)
                .flatMap(oldData -> slideScannerRepository.deleteByDeviceSerialNumber(deviceSerialNumber)
                        .flatMap(count -> {
                            if (count == 0) {
                                return Mono.error(new ResourceNotFoundException(ERROR_MSG + deviceSerialNumber));
                            }
                            return redisStore.deleteKeysByPattern(SCANNER_DEVICE_PREFIX + "*")
                                    .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_SCANNER_DEVICE_PREFIX + "*"))
                                    .then(notificationService.notifyEntityChange("scanner", oldData, null))
                                    .thenReturn(true);
                        }))
                .doOnSuccess(v -> log.info("Slide Scanner deleted successfully with deviceSerialNumber: {}", deviceSerialNumber))
                .doOnError(e -> log.error("Failed to delete slide scanner with deviceSerialNumber {}: {}", deviceSerialNumber, e.getMessage(), e));
    }

    public Mono<String> getResearchDicomStoreUrl() {
        return configStore.get(RESEARCH_DICOM_STORE, DEFAULT_APPLICATION);
    }

    private String extractDatasetId(String dicomStorePath) {
        if (dicomStorePath == null || dicomStorePath.isBlank()) {
            throw new InternalServerException("dicomStorePath must not be null or blank");
        }
        String[] parts = dicomStorePath.trim().split("/");
        if (parts.length < 8 || !"projects".equals(parts[0]) || !"locations".equals(parts[2])
                || !"datasets".equals(parts[4]) || !"dicomStores".equals(parts[6])) {
            throw new IllegalArgumentException("Invalid DICOM store path: " + dicomStorePath);
        }
        return parts[5];
    }

    private Map<String, List<String>> excludeResearchUrl(Map<String, List<String>> dicomStores, @Nullable String researchUrl) {
        if (!StringUtils.hasText(researchUrl)) return dicomStores;

        String datasetId = extractDatasetId(researchUrl);
        List<String> stores = dicomStores.get(datasetId);
        if (stores == null) return dicomStores;

        List<String> filtered = stores.stream()
                .filter(url -> !url.equals(researchUrl)).toList();

        if (filtered.isEmpty()) {
            dicomStores.remove(datasetId);
        } else {
            dicomStores.put(datasetId, filtered);
        }
        return dicomStores;
    }

}