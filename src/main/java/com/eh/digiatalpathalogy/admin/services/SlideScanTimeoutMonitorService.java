package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.config.SlideScanProgressConfig;
import com.eh.digiatalpathalogy.admin.entity.DicomInstance;
import com.eh.digiatalpathalogy.admin.entity.SlideScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.ScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.model.scanstatus.TimeoutDecision;
import com.eh.digiatalpathalogy.admin.repository.DicomInstanceRepository;
import com.eh.digiatalpathalogy.admin.repository.SlideScanStatusRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.EH_ADMIN_CONSOLE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SLIDE_SCAN_STATUS_PREFIX;
import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.*;

/**
 * Scheduler service that detects stuck slide scans and marks them
 * as FAILED or WARNING based on workflow completion state.
 */
@Service
public class SlideScanTimeoutMonitorService {

    private static final Logger log = LoggerFactory.getLogger(SlideScanTimeoutMonitorService.class);

    private final RedisEntityStore redisEntityStore;
    private final SlideScanStatusService slideScanStatusService;
    private final SlideScanStatusRepository repository;
    private final ConfigStore configStore;
    private final SlideScanProgressConfig slideScanProgressConfig;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final String instanceId = UUID.randomUUID().toString();

    public SlideScanTimeoutMonitorService(RedisEntityStore redisEntityStore, SlideScanStatusService slideScanStatusService, SlideScanStatusRepository repository, ConfigStore configStore, SlideScanProgressConfig slideScanProgressConfig, DicomInstanceRepository dicomInstanceRepository) {
        this.redisEntityStore = redisEntityStore;
        this.slideScanStatusService = slideScanStatusService;
        this.repository = repository;
        this.configStore = configStore;
        this.slideScanProgressConfig = slideScanProgressConfig;
        this.dicomInstanceRepository = dicomInstanceRepository;
    }

    /**
     * Periodically scans active slide processes and triggers timeout handling
     * using distributed locking to ensure single-instance execution.
     */
    @Scheduled(
            fixedDelayString = "#{@slideScanProgressConfig.getTimeout().getCheck().getInterval() * 60000}",
            initialDelayString = "#{@slideScanProgressConfig.getTimeout().getCheck().getInitialDelay() * 60000}"
    )
    public void monitorTimeoutScans() {

        if (slideScanProgressConfig.getTimeout() == null || !slideScanProgressConfig.getTimeout().isEnabled()) {
            log.debug("Timeout monitor disabled");
            return;
        }

        Duration lockTtl = Duration.ofMinutes(slideScanProgressConfig.getTimeout().getLock().getTtlMinutes());
        long timeoutMinutes = slideScanProgressConfig.getTimeout().getMinutes();
        int parallelism = slideScanProgressConfig.getTimeout().getProcessing().getParallelism();
        log.info("Starting slide scan timeout monitoring job [instanceId={}, timeoutMinutes={}, lockTtl={}m, parallelism={}]", instanceId, timeoutMinutes, lockTtl.toMinutes(), parallelism);
        redisEntityStore.acquireLock(LOCK_KEY, instanceId, lockTtl)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.info("Skipping timeout monitoring job. Distributed lock is already held by another instance [lockKey={}, instanceId={}]", LOCK_KEY, instanceId);
                        return Mono.empty();
                    }
                    log.info("Lock acquired, starting timeout job");
                    long threshold = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES).toEpochMilli();
                    return redisEntityStore.getExpired(ACTIVE_SCAN_KEY, threshold)
                            .flatMap(this::processBarcode, parallelism)
                            .then(redisEntityStore.releaseLock(LOCK_KEY, instanceId))
                            .doOnSuccess(released -> log.info("Slide scan timeout monitoring job completed successfully. Lock released [lockKey={}, released={}, instanceId={}]", LOCK_KEY, released, instanceId))
                            .onErrorResume(error -> {
                                log.error("Unexpected error during timeout processing. Releasing lock [instanceId={}]", instanceId, error);
                                return redisEntityStore
                                        .releaseLock(LOCK_KEY, instanceId)
                                        .doOnSuccess(released -> log.info("Lock release attempted after failure [lockKey={}, released={}, instanceId={}]", LOCK_KEY, released, instanceId));
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to acquire distributed lock for slide scan timeout monitor [lockKey={}, instanceId={}]", LOCK_KEY, instanceId, e);
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * Processes a single slide barcode by fetching state from cache/DB
     * and applying timeout decision logic.
     */
    private Mono<Void> processBarcode(String barcode) {

        String key = redisKey(barcode);
        log.info("Fetching from redis :: {}", key);
        return redisEntityStore.findByKey(key, SlideScanProgressEvent.class)
                .switchIfEmpty(fetchFromDbAndCache(barcode, key))
                .flatMap(event ->
                        processTimeout(event)
                                .doOnSuccess(v -> log.info("Removed barcode={} from ACTIVE set after timeout", barcode))
                                .then()
                )
                .onErrorResume(e -> {
                    log.error("Error processing barcode {}", barcode, e);
                    return Mono.empty();
                });
    }

    /**
     * Fallback to DB if Redis cache is missing and updates cache for future use.
     */
    private Mono<SlideScanProgressEvent> fetchFromDbAndCache(String barcode, String key) {
        return repository.findBySlideBarcode(barcode)
                .map(this::toEvent)
                .flatMap(event -> redisEntityStore.save(key, event).thenReturn(event));
    }

    /**
     * Applies timeout decision, updates DB, and emits event to downstream systems.
     */
    private Mono<Void> processTimeout(SlideScanProgressEvent event) {
        TimeoutDecision decision = decide(event);
        log.info("Processing timeout | decision={} | barcode={}", decision.status(), event.slideBarcode());
        return updateDicomInstances(event, decision.errorMessage())
                .then(sendTimeoutEvent(event, decision));
    }

    /**
     * Publishes timeout result as a slide scan status event to downstream consumers.
     */
    private Mono<Void> sendTimeoutEvent(SlideScanProgressEvent event, TimeoutDecision decision) {

        SlideScanProgressEvent timeoutEvent =
                new SlideScanProgressEvent(event.accessionNumber(), event.slideBarcode(), event.seriesId(), event.deviceSerialNumber(), decision.status(),
                        EH_ADMIN_CONSOLE, event.progressPercent(), event.progressEvents(), decision.errorMessage(), SLIDE_STATUS);
        return slideScanStatusService.streamSlideScanStatus(timeoutEvent);
    }

    /**
     * Determines final scan outcome based on workflow progress:
     * before enrichment → FAILED, after → WARNING COMPLETED.
     */
    private TimeoutDecision decide(SlideScanProgressEvent event) {

        Set<ScanStatus> progressEvents = event.progressEvents();
        if (progressEvents == null || progressEvents.isEmpty()) {
            return new TimeoutDecision(SCAN_FAILED, "System-timeout : No events found");
        }

        Set<String> statuses = progressEvents.stream()
                .map(ScanStatus::scanStatus)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (!statuses.contains("enrichment-completed")) {
            return new TimeoutDecision(SCAN_FAILED, "System-timeout : Stuck before enrichment");
        }
        boolean synapseMissing = configStore.getSynapseEnabled() && !statuses.contains("synapse-started") && !statuses.contains("synapse-completed");
        if (synapseMissing) {
            return new TimeoutDecision(TIMEOUT_WARNING_COMPLETED, "Synapse workflow not executed");
        }
        return new TimeoutDecision(TIMEOUT_WARNING_COMPLETED, "Warning completed by scheduler timeout");
    }

    /**
     * Updates DICOM instance with timeout error message for traceability.
     */
    private Mono<Boolean> updateDicomInstances(SlideScanProgressEvent event, String errorMessage) {

        Query query = Query.query(
                Criteria.where("barcode").is(event.slideBarcode())
                        .and("seriesInstanceUid").is(event.seriesId())
        );

        Update update = new Update()
                .set("errorMessage", errorMessage)
                .set("processingStatus", "TIMEOUT_FAILED");

        return dicomInstanceRepository.updateMany(query, update, DicomInstance.class)
                .map(Objects::nonNull)
                .defaultIfEmpty(false)
                .doOnSuccess(updated -> log.info("DicomInstance update status={} barcode={} seriesId={}", updated, event.slideBarcode(), event.seriesId()))
                .doOnError(e -> log.error("Failed to update DicomInstance barcode={} seriesId={}", event.slideBarcode(), event.seriesId(), e));
    }

    /**
     * Generates Redis key for storing slide scan progress.
     */
    private String redisKey(String barcode) {
        return SLIDE_SCAN_STATUS_PREFIX + barcode;
    }

    /**
     * Converts DB entity to event model used in processing pipeline.
     */
    private SlideScanProgressEvent toEvent(SlideScanStatus s) {
        return new SlideScanProgressEvent(s.getAccessionNumber(), s.getSlideBarcode(), s.getSeriesId(), s.getDeviceSerialNumber(), s.getScanStatus(), null, s.getProgressPercent(), s.getProgressEvents(), null, null);
    }


    @PostConstruct
    public void logTimeoutConfig() {
        if (slideScanProgressConfig.getTimeout() != null) {
            log.info(
                    "SlideScanTimeoutMonitor initialized [enabled={}, timeout={}m, interval={}m, parallelism={}]",
                    slideScanProgressConfig.getTimeout().isEnabled(),
                    slideScanProgressConfig.getTimeout().getMinutes(),
                    slideScanProgressConfig.getTimeout().getCheck().getInterval(),
                    slideScanProgressConfig.getTimeout().getProcessing().getParallelism()
            );
        }
    }


}
