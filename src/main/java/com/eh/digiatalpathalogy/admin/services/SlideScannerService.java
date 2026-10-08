package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.NotificationEntityType;
import com.eh.digiatalpathalogy.admin.repository.SlideScannerRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
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
import static com.eh.digiatalpathalogy.admin.constant.SlideScannerFields.*;

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

        return slideScannerRepository.save(slideScanner)
                .flatMap(saved -> redisStore.deleteKeysByPattern(SCANNER_DEVICE_PREFIX + "*")
                        .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_SCANNER_DEVICE_PREFIX + "*"))
                        .then(notificationService.notifyEntityChange(NotificationEntityType.SCANNER.getKey(), null, saved))
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Slide scanner created: DeviceSerialNumber={}", saved.getDeviceSerialNumber()))
                .onErrorMap(DuplicateKeyException.class, ex -> {
                    log.warn("Duplicate entry: DeviceSerialNumber={}", slideScanner.getDeviceSerialNumber(), ex);
                    return new HttpRequestException(HttpStatus.CONFLICT, "Slide scanner with the same Device SerialNumber or ID already exists.");
                })
                .doOnError(error -> log.error("Slide scanner creation failed: {}", error.getMessage(), error));
    }

    public Mono<SlideScanner> updateByDeviceSerialNumber(String deviceSerialNumber, Map<String, Object> updates) {

        if (!StringUtils.hasText(deviceSerialNumber)) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "DeviceSerialNumber must not be blank."));
        }
        if (updates == null) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Update payload must not be null."));
        }

        final boolean incomingResearch = Boolean.TRUE.equals(updates.get(RESEARCH));
        final String incomingDicomStore = (String) updates.get(DICOM_STORE);

        log.info("updateByDeviceSerialNumber: START DeviceSerialNumber={} updates={}", deviceSerialNumber, updates);

        return getByDeviceSerialNumber(deviceSerialNumber)
                .doOnNext(oldData -> log.info("updateByDeviceSerialNumber: fetched oldData DeviceSerialNumber={} oldData={}", deviceSerialNumber, describe(oldData)))
                .flatMap(existing -> {
                    SlideScanner backupScannerObj = takeBackup(existing);
                    applyUpdates(existing, updates);
                    // when scanner is of type research
                    if (incomingResearch && StringUtils.hasText(incomingDicomStore)) {
                        existing.setDepartment(null);
                        existing.setDicomStore(null);
                        log.info("updateByDeviceSerialNumber: research+dicomStore clear rule fired DeviceSerialNumber={}", deviceSerialNumber);
                    }

                    log.info("updateByDeviceSerialNumber: entity after merge (this is what gets saved) DeviceSerialNumber={} merged={}", deviceSerialNumber, describe(existing));

                    return slideScannerRepository.save(existing)
                            .doOnNext(updated -> log.info("updateByDeviceSerialNumber: Mongo save result DeviceSerialNumber={} updated={}", deviceSerialNumber, describe(updated)))
                            .flatMap(updated -> {
                                Mono<Void> invalidateCache = redisStore.deleteKeysByPattern(SCANNER_DEVICE_PREFIX + "*")
                                        .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_SCANNER_DEVICE_PREFIX + "*"))
                                        .then();
                                Mono<SlideScanner> result = incomingResearch ? getByDeviceSerialNumber(deviceSerialNumber) : Mono.just(updated);
                                return Mono.whenDelayError(invalidateCache)
                                        .then(result)
                                        .doOnNext(finalResult -> log.info("updateByDeviceSerialNumber: finalResult DeviceSerialNumber={} finalResult={}", deviceSerialNumber, describe(finalResult)))
                                        .flatMap(finalResult -> notificationService.notifyEntityChange(NotificationEntityType.SCANNER.getKey(), backupScannerObj, finalResult)
                                                .thenReturn(finalResult));
                            });
                })
                .doOnSuccess(updated -> log.info("Slide scanner updated successfully: DeviceSerialNumber={}", updated.getDeviceSerialNumber()))
                .doOnError(error -> log.error("Failed to update slide scanner with DeviceSerialNumber={}: {}", deviceSerialNumber, error.getMessage(), error));
    }

    private void applyUpdates(SlideScanner entity, Map<String, Object> updates) {
        if (updates.containsKey(NAME)) entity.setName((String) updates.get(NAME));
        if (updates.containsKey(MODEL)) entity.setModel(blankToNull((String) updates.get(MODEL)));
        if (updates.containsKey(SCANNER_TYPE)) entity.setScannerType((String) updates.get(SCANNER_TYPE));
        if (updates.containsKey(LOCATION)) entity.setLocation((String) updates.get(LOCATION));
        if (updates.containsKey(DEPARTMENT)) entity.setDepartment((String) updates.get(DEPARTMENT));
        if (updates.containsKey(DICOM_STORE)) entity.setDicomStore((String) updates.get(DICOM_STORE));
        if (updates.containsKey(AE_TITLE)) entity.setAeTitle((String) updates.get(AE_TITLE));
        if (updates.containsKey(PORT)) entity.setPort(blankToNull((String) updates.get(PORT)));
        if (updates.containsKey(HOSPITAL_NAME)) entity.setHospitalName(blankToNull((String) updates.get(HOSPITAL_NAME)));
        if (updates.containsKey(IP_ADDRESS)) entity.setIpAddress(blankToNull((String) updates.get(IP_ADDRESS)));
        if (updates.containsKey(VENDOR)) entity.setVendor(blankToNull((String) updates.get(VENDOR)));
        if (updates.containsKey(RESEARCH)) entity.setResearch((Boolean) updates.get(RESEARCH));
        if (updates.containsKey(CONNECTED)) entity.setConnected((Boolean) updates.get(CONNECTED));
        if (updates.containsKey(REMOTE_AE_TITLE)) entity.setRemoteAeTitle(blankToNull((String) updates.get(REMOTE_AE_TITLE)));
        if (updates.containsKey(REMOTE_HOST)) entity.setRemoteHost(blankToNull((String) updates.get(REMOTE_HOST)));
        if (updates.containsKey(REMOTE_PORT)) entity.setRemotePort(toInteger(updates.get(REMOTE_PORT)));
        if (updates.containsKey(STORAGE_STRATEGY)) entity.setStorageStrategy(blankToNull((String) updates.get(STORAGE_STRATEGY)));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            String text = value.toString().trim();
            return text.isEmpty() ? null : Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SlideScanner takeBackup(SlideScanner s) {
        SlideScanner copy = new SlideScanner();
        BeanUtils.copyProperties(s, copy);
        log.info("takeBackup for comparison :  BackupObj={}",  describe(s));
        return copy;
    }

    private String describe(SlideScanner s) {
        if (s == null) return "null";
        return "{id=" + s.getId()
                + ", deviceSerialNumber=" + s.getDeviceSerialNumber()
                + ", name=" + s.getName()
                + ", model=" + s.getModel()
                + ", scannerType=" + s.getScannerType()
                + ", location=" + s.getLocation()
                + ", department=" + s.getDepartment()
                + ", dicomStore=" + s.getDicomStore()
                + ", aeTitle=" + s.getAeTitle()
                + ", port=" + s.getPort()
                + ", hospitalName=" + s.getHospitalName()
                + ", ipAddress=" + s.getIpAddress()
                + ", vendor=" + s.getVendor()
                + ", research=" + s.getResearch()
                + ", connected=" + s.getConnected()
                + ", remoteAeTitle=" + s.getRemoteAeTitle()
                + ", remoteHost=" + s.getRemoteHost()
                + ", remotePort=" + s.getRemotePort()
                + ", storageStrategy=" + s.getStorageStrategy()
                + "}";
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
                                    .then(notificationService.notifyEntityChange(NotificationEntityType.SCANNER.getKey(), oldData, null))
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