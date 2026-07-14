package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.config.SlideScanProgressConfig;
import com.eh.digiatalpathalogy.admin.config.SlideScanStatusStream;
import com.eh.digiatalpathalogy.admin.entity.SlideScanStatus;
import com.eh.digiatalpathalogy.admin.exception.InvalidScanProgressException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.PageResponse;
import com.eh.digiatalpathalogy.admin.model.scanstatus.DicomInstanceDto;
import com.eh.digiatalpathalogy.admin.model.scanstatus.ScanStatus;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanProgressEvent;
import com.eh.digiatalpathalogy.admin.model.scanstatus.SlideScanStatusDto;
import com.eh.digiatalpathalogy.admin.repository.DicomInstanceRepository;
import com.eh.digiatalpathalogy.admin.repository.SlideScanStatusRepository;
import com.eh.digiatalpathalogy.admin.util.PageableUtils;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.eh.digiatalpathalogy.admin.constant.EnrichmentToolConstant.EH_ADMIN_CONSOLE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.ACTIVE_SCAN_KEY;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SLIDE_SCAN_STATUS_PREFIX;
import static com.eh.digiatalpathalogy.admin.constant.SlideScanStatusConstant.*;


/**
 * Service that tracks slide scan progress, updates status, and streams live updates via SSE.
 * Integrates MongoDB, Redis, and a status stream to maintain and publish scan state.
 */
@Service
@RefreshScope
public class SlideScanStatusService {

    private static final Logger log = LoggerFactory.getLogger(SlideScanStatusService.class);

    private final SlideScanStatusRepository slideScanStatusRepository;
    private final SlideScanStatusStream statusStream;
    private final SlideScanProgressConfig slideScanProgressConfig;
    private final DicomInstanceRepository dicomInstanceRepository;
    private final RedisEntityStore redisEntityStore;
    private final ConfigStore configStore;

    public SlideScanStatusService(SlideScanStatusRepository slideScanStatusRepository, SlideScanStatusStream statusStream, SlideScanProgressConfig slideScanProgressConfig, DicomInstanceRepository dicomInstanceRepository, RedisEntityStore redisEntityStore, ConfigStore configStore) {
        this.slideScanStatusRepository = slideScanStatusRepository;
        this.statusStream = statusStream;
        this.slideScanProgressConfig = slideScanProgressConfig;
        this.dicomInstanceRepository = dicomInstanceRepository;
        this.redisEntityStore = redisEntityStore;
        this.configStore = configStore;
    }

    /**
     * Returns a paged list of slide scans filtered by status with total count.
     * Falls back to empty page when status is blank or unsupported.
     */
    public Mono<PageResponse<SlideScanStatusDto>> getSlideScanByStatus(Integer page, Integer size, String scanStatus) {

        String status = (scanStatus == null) ? "" : scanStatus.trim();
        Pageable pageable = PageableUtils.create(page, size, UPDATED_AT, Sort.Direction.DESC);

        if (!StringUtils.hasText(status)) {
            log.info("Blank scanStatus filter received; returning empty page (page={}, size={})",
                    pageable.getPageNumber(), pageable.getPageSize());
            return Mono.just(PageResponse.of(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0L));
        }

        Tuple2<Flux<SlideScanStatusDto>, Mono<Long>> selection = switch (status) {
            case IN_PROGRESS ->
                    Tuples.of(slideScanStatusRepository.findByScanStatusNotIn(INACTIVE_SCAN_STATUSES, pageable).map(slideScanStatus -> toScanStatusDto(slideScanStatus, false)),
                            slideScanStatusRepository.countByScanStatusNotIn(INACTIVE_SCAN_STATUSES));
            case SCAN_COMPLETED ->
                    Tuples.of(slideScanStatusRepository.findByScanStatusIn(COMPLETE_SCAN_STATUSES, pageable).map(slideScanStatus -> toScanStatusDto(slideScanStatus, false)),
                            slideScanStatusRepository.countByScanStatusIn(COMPLETE_SCAN_STATUSES));
            case SCAN_FAILED ->
                    Tuples.of(slideScanStatusRepository.findByScanStatus(status, pageable).map(slideScanStatus -> toScanStatusDto(slideScanStatus, false)),
                            slideScanStatusRepository.countByScanStatus(status));
            default -> {
                log.warn("Unsupported scanStatus filter received; returning empty page (status='{}')", status);
                yield Tuples.of(Flux.empty(), Mono.just(0L));
            }

        };

        Flux<SlideScanStatusDto> contentFlux = selection.getT1();
        Mono<Long> totalMono = selection.getT2();

        return Mono.zip(contentFlux.collectList(), totalMono)
                .map(tuple -> PageResponse.of(tuple.getT1(), pageable.getPageNumber(), pageable.getPageSize(),
                        tuple.getT2()));
    }

    /**
     * Creates a new slide scan status entry.
     * Simply persists and returns the saved entity.
     */
    public Mono<SlideScanStatus> create(SlideScanStatus slide) {
        return slideScanStatusRepository.save(slide);
    }

    /**
     * Publishes a scan status DTO to the status stream for live updates.
     */
    private void publishToStream(SlideScanStatusDto slideScanStatusDto) {
        statusStream.publish(slideScanStatusDto);
    }

    /**
     * Streams active slide scan updates as SSE, including periodic heartbeats.
     * Applies backpressure buffering to handle slow clients.
     */
    public Flux<ServerSentEvent<Object>> streamInProgressSse() {
        Flux<ServerSentEvent<Object>> slideEvents = statusStream.streamActiveSlides()
                .map(slide -> ServerSentEvent
                        .builder()
                        .event("message")
                        .data(slide)
                        .build())
                .doOnSubscribe(s -> log.info("SSE subscribed"))
                .doOnCancel(() -> log.info("SSE disconnected"))
                .doOnError(e -> log.error("SSE stream error: active slide scan stream failed", e));
        return Flux.merge(slideEvents, statusStream.getHeartbeat())
                .onBackpressureBuffer();
    }

    /**
     * Validates, de-duplicates, and applies an incoming progress event to storage and cache.
     * Emits nothing on success; logs and propagates errors on failure.
     */
    public Mono<Void> streamSlideScanStatus(SlideScanProgressEvent event) {
        return Mono.defer(() -> {
            if (RESEARCH_EVENT.equalsIgnoreCase(event.eventType())) {
                statusStream.publishResearchSignal();
                return Mono.empty();
            }
            return validateEvent(event)
                    .doOnSuccess(e -> log.debug("Validation passed (barcode={}, status={})", e.slideBarcode(), e.scanStatus()))
                    .flatMap(validEvent ->
                            fetchPreviousEvent(validEvent.slideBarcode())
                                    .materialize()
                                    .flatMap(signal -> processEvent(signal, validEvent))
                    )
                    .doOnSuccess(v -> log.debug("Event processed successfully (barcode={}, status={})", event.slideBarcode(), event.scanStatus()))
                    .doOnError(e -> log.error("Update failed: barcode={}, status={}, error={}", event.slideBarcode(), event.scanStatus(), e.getMessage(), e));
        });
    }

    /**
     * Applies event logic based on presence of a previous event and de-duplication rules.
     * Skips synapse first events and double events.
     */
    private Mono<Void> processEvent(Signal<SlideScanProgressEvent> signal, SlideScanProgressEvent newValidEvent) {

        if (!signal.hasValue()) {
            final boolean isSynapseQuery = newValidEvent.scanStatus().equalsIgnoreCase("synapse-started") || newValidEvent.scanStatus().equalsIgnoreCase("synapse-completed") || newValidEvent.scanStatus().equalsIgnoreCase("synapse-failed");
            if (isSynapseQuery) {
                log.info("Ignoring synapse event without prior state (barcode={}, status={}, sourceService={})", newValidEvent.slideBarcode(), newValidEvent.scanStatus(), newValidEvent.sourceService());
                return Mono.empty();
            }
            if (!StringUtils.hasText(newValidEvent.seriesId())) {
                log.warn("Invalid scan progress event received: seriesId is null or blank (sourceService={}, status={})", newValidEvent.sourceService(), newValidEvent.scanStatus());
                return Mono.error(new InvalidScanProgressException(newValidEvent.slideBarcode(), "seriesId is null or blank"));
            }
            return upsertAndCache(newValidEvent, null).then();
        }

        SlideScanProgressEvent prevEvent = signal.get();
        boolean isRescan = isRescan(prevEvent, newValidEvent);
        if (!isRescan && isDoubleEvent(prevEvent, newValidEvent.scanStatus())) {
            log.info("Duplicate non-enrichment event ignored (barcode={}, status={})", newValidEvent.slideBarcode(), newValidEvent.scanStatus());
            return Mono.empty();
        }
        return upsertAndCache(newValidEvent, prevEvent).then();
    }

    /**
     * Ensures the event has a valid barcode and status that exists in configuration.
     */
    private Mono<SlideScanProgressEvent> validateEvent(SlideScanProgressEvent event) {

        if (event == null) {
            log.warn("Invalid scan progress event received: event is null");
            return Mono.error(new InvalidScanProgressException(null, "slideScanProgressEvent is null"));
        }
        final String barcode = event.slideBarcode();
        if (!StringUtils.hasText(barcode)) {
            log.warn("Invalid scan progress event received: slideBarcode is null or blank (sourceService={}, status={})", event.sourceService(), event.scanStatus());
            return Mono.error(new InvalidScanProgressException(barcode, "slideBarcode is null or blank"));
        }
        if (configStore.getValidScanStatus() == null || !configStore.getValidScanStatus().contains(event.scanStatus())) {
            log.warn("Invalid scan status received (barcode={}, sourceService={}, status={})", barcode, event.sourceService(), event.scanStatus());
            return Mono.error(new InvalidScanProgressException(barcode, "Scan service: " + event.sourceService() + " Invalid scan status: " + event.scanStatus()));
        }
        return Mono.just(event);
    }

    /**
     * Retrieves the previous event from Redis or falls back to MongoDB if not cached.
     */
    private Mono<SlideScanProgressEvent> fetchPreviousEvent(String barcode) {
        final String key = redisKey(barcode);
        return redisEntityStore.findByKey(key, SlideScanProgressEvent.class)
                .doOnNext(e -> log.debug("Previous event cache hit (barcode={}, redisKey={}, prevStatus={}, prevProgress={})", barcode, key, e.scanStatus(), e.progressPercent()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Previous event cache miss; falling back to MongoDB (barcode={}, redisKey={})", barcode, key);
                    return slideScanStatusRepository.findBySlideBarcode(barcode)
                            .map(this::toEvent)
                            .doOnNext(e -> log.debug("Previous event loaded from MongoDB (barcode={}, prevStatus={}, prevProgress={})", barcode, e.scanStatus(), e.progressPercent()));
                }));
    }

    /**
     * Returns true if the incoming status has already been recorded (non-enrichment).
     */
    private boolean isDoubleEvent(SlideScanProgressEvent previous, String incomingStatus) {
        if (previous == null) return false;
        Set<ScanStatus> progressEvents = previous.progressEvents();
        if (progressEvents == null) return false;

        boolean isEnrichment = ENRICHMENT_IN_PROGRESS_STATUS.equalsIgnoreCase(incomingStatus);
        boolean duplicateScanEvent = progressEvents.stream().anyMatch(st -> st.scanStatus().equalsIgnoreCase(incomingStatus));
        boolean isDuplicate = !isEnrichment && duplicateScanEvent;

        if (isDuplicate) {
            log.debug("Detected duplicate event (prevStatus={}, incomingStatus={})", previous.scanStatus(), incomingStatus);
        }
        return isDuplicate;
    }

    /**
     * Persists the new progress (insert/update) and updates Redis cache.
     * Publishes a DTO to the stream after storage.
     */
    private Mono<SlideScanProgressEvent> upsertAndCache(SlideScanProgressEvent newEvent, SlideScanProgressEvent lastScanEvent) {

        final String barcode = newEvent.slideBarcode();
        final String status = newEvent.scanStatus();
        final Query barcodeQuery = Query.query(Criteria.where(SLIDE_BARCODE).is(barcode));

        boolean isRescan = isRescan(lastScanEvent, newEvent);
        double lastPercent = isRescan ? 0.0 : safeProgressPercent(lastScanEvent);
        Double newPercent = computeNewProgressPercent(newEvent.sourceService(), status, lastPercent);

        Update update = isRescan ? buildRescanUpsertQuery(newPercent, newEvent, lastScanEvent) : buildUpsertQuery(newPercent, newEvent, lastScanEvent);

        return slideScanStatusRepository.saveOrUpdate(barcodeQuery, update, SlideScanStatus.class)
                .doOnNext(savedScan -> publishToStream(toScanStatusDto(savedScan, true)))
                .flatMap(e -> finalizeAndCache(e, redisKey(barcode)))
                .doOnSuccess(e -> log.debug("Upsert + cache completed (barcode={}, finalStatus={}, finalProgress={})", e != null ? e.slideBarcode() : null, e != null ? e.scanStatus() : null, e != null ? e.progressPercent() : null))
                .doOnError(ex -> log.error("Upsert + cache failed (barcode={}, status={}, sourceService={})", barcode, status, newEvent.sourceService(), ex));
    }

    /**
     * Safely reads the prior progress percent, defaulting to 0 when missing.
     */
    private Double safeProgressPercent(SlideScanProgressEvent scanProgressEvent) {
        if (scanProgressEvent == null) return 0.0;
        try {
            Double pct = scanProgressEvent.progressPercent();
            return pct != null ? pct : 0.0;
        } catch (Exception ignored) {
            log.debug("Unable to read prior progressPercent; defaulting to 0");
            return 0.0;
        }
    }

    /**
     * Computes the next progress percent based on per-status config and last value.
     * Handles enrichment, completion, and warning-completed edge cases.
     */
    private Double computeNewProgressPercent(String sourceService, String status, double lastScanPercent) {

        double newPercent;
        final double configPercentageByScanStatus = getConfigPercentageByScanStatus(sourceService, status);
        if (EH_ADMIN_CONSOLE.equalsIgnoreCase(sourceService)) {
            newPercent = lastScanPercent;
        } else if (configPercentageByScanStatus == 0) {
            newPercent = 0;
        } else if (configPercentageByScanStatus < 0) {
            newPercent = lastScanPercent;
        } else if (ENRICHMENT_IN_PROGRESS_STATUS.equalsIgnoreCase(status)) {
            newPercent = lastScanPercent <= 40 ? lastScanPercent + configPercentageByScanStatus : lastScanPercent;
        } else if ("enrichment-completed".equalsIgnoreCase(status)) {
            newPercent = 50.0;
        } else {
            newPercent = lastScanPercent + configPercentageByScanStatus;
        }
        log.debug("Compute progress percent (sourceService={}, status={}, lastProgress={}, configDelta={})", sourceService, status, lastScanPercent, configPercentageByScanStatus);
        return newPercent;
    }

    /**
     * Builds a MongoDB upsert Update with status, progress, timestamps, and metadata.
     * Merges progress events and sets completion to 100% when all milestones are done.
     */
    private Update buildUpsertQuery(Double newProgressPercentage, SlideScanProgressEvent newEvent, SlideScanProgressEvent lastScanEvent) {

        final LinkedHashSet<ScanStatus> newProgressEvent = lastScanEvent != null && lastScanEvent.progressEvents() != null
                ? new LinkedHashSet<>(lastScanEvent.progressEvents())
                : new LinkedHashSet<>();
        newProgressEvent.add(buildScanProgressEvent(newEvent));

        Set<String> scanEvents = newProgressEvent.stream().map(ScanStatus::scanStatus).collect(Collectors.toSet());
        if (scanEvents.contains("enrichment-completed") && scanEvents.contains("exported") && scanEvents.contains("synapse-completed") && scanEvents.contains("ibex-classification-finished")) {
            newProgressPercentage = 100.0;
        }
        String status = setSlideScanStatus(newProgressPercentage, newEvent.scanStatus(), lastScanEvent);
        Update update = new Update()
                .set("scanStatus", status)
                .set("progressPercent", newProgressPercentage)
                .set(UPDATED_AT, Instant.now())
                .set(PROGRESS_EVENTS, newProgressEvent)
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert(SLIDE_BARCODE, newEvent.slideBarcode())
                .setOnInsert("deviceSerialNumber", newEvent.deviceSerialNumber());

        if (StringUtils.hasText(newEvent.accessionNumber())) {
            update.set("accessionNumber", newEvent.accessionNumber());
        }
        if (StringUtils.hasText(newEvent.seriesId())) {
            update.set(SERIES_ID, newEvent.seriesId());
        }
        return update;
    }


    private Update buildRescanUpsertQuery(Double newPercent, SlideScanProgressEvent newEvent, SlideScanProgressEvent lastScanEvent) {
        log.info("RESCAN detected (barcode={}, oldSeriesId={}, newSeriesId={})", lastScanEvent.slideBarcode(), lastScanEvent.seriesId(), newEvent.seriesId());

        SlideScanStatus historySnapshot = toHistorySnapshot(lastScanEvent);
        LinkedHashSet<ScanStatus> resetEvents = new LinkedHashSet<>();
        resetEvents.add(buildScanProgressEvent(newEvent));

        return new Update()
                .push("scanHistory", historySnapshot)
                .set(PROGRESS_EVENTS, resetEvents)
                .set("progressPercent", newPercent)
                .set("scanStatus", newEvent.scanStatus())
                .set(SERIES_ID, newEvent.seriesId())
                .set(UPDATED_AT, Instant.now())
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert(SLIDE_BARCODE, newEvent.slideBarcode())
                .setOnInsert("deviceSerialNumber", newEvent.deviceSerialNumber());

    }

    private ScanStatus buildScanProgressEvent(SlideScanProgressEvent scanProgressEvent) {
        String type = "scan-event";
        if (WARNING_COMPLETED.contains(scanProgressEvent.scanStatus()) || EH_ADMIN_CONSOLE.equalsIgnoreCase(scanProgressEvent.sourceService())) {
            type = "warning";
        }
        return new ScanStatus(type, scanProgressEvent.scanStatus(), scanProgressEvent.errorMessage());
    }

    /**
     * Determines the persisted scan status from progress percent and failure states.
     */
    private String setSlideScanStatus(Double progressPercentage, String scanStatus, SlideScanProgressEvent lastScanEvent) {

        if (SCAN_FAILED_STATUS.contains(scanStatus)) {
            return SCAN_FAILED;
        }
        if (lastScanEvent != null && SCAN_WARNING_COMPLETED.equalsIgnoreCase(lastScanEvent.scanStatus())) {
            return SCAN_WARNING_COMPLETED;
        }
        if (WARNING_COMPLETED.contains(scanStatus)) {
            return SCAN_WARNING_COMPLETED;
        }
        if (progressPercentage != null && Double.compare(progressPercentage, 100.0) == 0) {
            return SCAN_COMPLETED;
        }
        return scanStatus;
    }

    /**
     * Looks up the configured progress percent for a given service and status.
     * Throws detailed errors when service or status is not configured.
     */
    public Double getConfigPercentageByScanStatus(String sourceService, String scanStatus) {

        if (sourceService == null || sourceService.isBlank()) {
            log.warn("Progress configuration lookup failed: sourceService is null/blank");
            throw new InvalidScanProgressException("Source service is null or blank");
        }
        if (scanStatus == null || scanStatus.isBlank()) {
            log.warn("Progress configuration lookup failed: scanStatus is null/blank (sourceService={})", sourceService);
            throw new InvalidScanProgressException("Scan status is null or blank");
        }
        Map<String, Double> serviceConfig = slideScanProgressConfig.getServices().get(sourceService);
        if (serviceConfig == null || serviceConfig.isEmpty()) {
            log.error("No progress configuration found for source service [{}]. Available services: {}", sourceService, slideScanProgressConfig.getServices().keySet());
            throw new InvalidScanProgressException(String.format("No progress configuration found for source service [%s]. Available services: %s", sourceService, slideScanProgressConfig.getServices().keySet()));
        }

        Double percentage = serviceConfig.get(scanStatus);
        if (percentage == null) {
            log.error("No percentage configured for status [{}] under service [{}]. Configured statuses: {}", scanStatus, sourceService, serviceConfig.keySet());
            throw new InvalidScanProgressException(String.format("No percentage configured for status [%s] under service [%s].", scanStatus, sourceService));
        }

        return percentage;
    }

    /**
     * Fetches a slide's scan status by barcode or emits not-found.
     */
    public Mono<SlideScanStatusDto> getSlideScanStatusByBarcode(String barcode) {
        return slideScanStatusRepository.findBySlideBarcode(barcode)
                .map(slideScanStatus -> toScanStatusDto(slideScanStatus, false))
                .doOnSuccess(dto -> {
                    if (dto != null) {
                        log.info("Slide scan status found (barcode={}, status={}, progress={})", dto.slideBarcode(), dto.scanStatus(), dto.progressPercent());
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Slide scan report not found (barcode={})", barcode);
                    return Mono.error(new ResourceNotFoundException("Slide scan report not found with barcode: " + barcode));
                }));
    }

    /**
     * Lists all DICOM instances associated with a slide barcode.
     */
    public Flux<DicomInstanceDto> getDicomInstancesByBarcode(String barcode, String seriesId) {
        log.debug("Fetching DICOM instances by barcode (barcode={}) and seriesId (seriesId={})", barcode, seriesId);
        return dicomInstanceRepository.findAllByBarcodeAndSeriesInstanceUid(barcode, seriesId)
                .map(DicomInstanceDto::from)
                .doOnComplete(() -> log.info("DICOM instances stream completed (barcode={}) and seriesId (seriesId={})", barcode, seriesId))
                .doOnError(e -> log.error("Failed to fetch DICOM instances (barcode={}) and seriesId (seriesId={})", barcode, seriesId, e));
    }

    /**
     * Returns all slide barcodes currently known to the system.
     */
    public Mono<List<String>> getAllSlideBarcodes() {
        return slideScanStatusRepository.findAllSlideBarcodes().collectList();
    }

    private SlideScanProgressEvent toEvent(SlideScanStatus scanStatus) {
        return new SlideScanProgressEvent(scanStatus.getAccessionNumber(), scanStatus.getSlideBarcode(), scanStatus.getSeriesId(),
                scanStatus.getDeviceSerialNumber(), scanStatus.getScanStatus(), null, scanStatus.getProgressPercent(), scanStatus.getProgressEvents(), null, null);
    }

    private boolean isTerminal(SlideScanProgressEvent updatedEvent) {
        return (updatedEvent.progressPercent() >= 100.0)
                || SCAN_FAILED.equalsIgnoreCase(updatedEvent.scanStatus())
                || SCAN_COMPLETED.equalsIgnoreCase(updatedEvent.scanStatus())
                || WARNING_COMPLETED.contains(updatedEvent.scanStatus());
    }

    private String redisKey(String barcode) {
        return SLIDE_SCAN_STATUS_PREFIX + barcode;
    }

    private boolean isRescan(SlideScanProgressEvent prev, SlideScanProgressEvent curr) {
        if (prev == null) return false;
        return StringUtils.hasText(curr.seriesId()) && StringUtils.hasText(prev.seriesId()) && !curr.seriesId().equals(prev.seriesId());
    }

    private SlideScanStatus toHistorySnapshot(SlideScanProgressEvent prev) {
        SlideScanStatus history = new SlideScanStatus();
        history.setAccessionNumber(prev.accessionNumber());
        history.setSlideBarcode(prev.slideBarcode());
        history.setDeviceSerialNumber(prev.deviceSerialNumber());
        history.setScanStatus(prev.scanStatus());
        history.setProgressPercent(prev.progressPercent());
        history.setProgressEvents(prev.progressEvents() != null ? new LinkedHashSet<>(prev.progressEvents()) : null);
        history.setSeriesId(prev.seriesId());
        history.setCreatedAt(Instant.now());
        history.setUpdatedAt(Instant.now());
        history.setScanHistory(null);
        return history;
    }

    private SlideScanStatusDto toScanStatusDto(SlideScanStatus slideScanStatus, boolean publishToStream) {
        return new SlideScanStatusDto(
                slideScanStatus.getId() != null ? slideScanStatus.getId().toHexString() : null, slideScanStatus.getAccessionNumber(),
                slideScanStatus.getSlideBarcode(), slideScanStatus.getSeriesId(), slideScanStatus.getDeviceSerialNumber(), slideScanStatus.getScanStatus(),
                slideScanStatus.getProgressPercent(), slideScanStatus.getCreatedAt(), slideScanStatus.getUpdatedAt(), publishToStream ? null : slideScanStatus.getProgressEvents());
    }

    /**
     * Updates Redis with the latest event, clearing cache for terminal states.
     */
    private Mono<SlideScanProgressEvent> finalizeAndCache(SlideScanStatus entity, String redisKey) {

        SlideScanProgressEvent updatedEvent = toEvent(entity);
        String barcode = entity.getSlideBarcode();
        long now = Instant.now().toEpochMilli();

        if (isTerminal(updatedEvent)) {
            return redisEntityStore.deleteByKey(redisKey)
                    .then(redisEntityStore.remove(ACTIVE_SCAN_KEY, barcode))
                    .thenReturn(updatedEvent);
        } else {
            log.debug("Updating Redis cache (barcode={}, redisKey={}, status={}, progress={})", updatedEvent.slideBarcode(), redisKey, updatedEvent.scanStatus(), updatedEvent.progressPercent());
            return redisEntityStore.save(redisKey, updatedEvent)
                    .then(redisEntityStore.add(ACTIVE_SCAN_KEY, barcode, now))
                    .thenReturn(updatedEvent);
        }
    }
}
