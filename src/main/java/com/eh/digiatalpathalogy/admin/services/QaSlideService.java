package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.QaSlideDetails;
import com.eh.digiatalpathalogy.admin.repository.QaSlideRepository;
import com.eh.digiatalpathalogy.admin.util.EncryptionUtils;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import com.eh.digiatalpathalogy.admin.services.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.DEFAULT_APPLICATION;
import static com.eh.digiatalpathalogy.admin.constant.ConfigKeys.PATH_QA_DICOM_STORE;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.DICOM_RECEIVER_PATH_QA_SLIDE_BARCODE_PREFIX;
import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.SLIDE_BARCODE_PREFIX;

@Service
public class QaSlideService {

    private static final Logger log = LoggerFactory.getLogger(QaSlideService.class);

    private static final String ERROR_MSG = "Slide not found with barcode: ";
    private final RedisEntityStore redisStore;
    private final ConfigStore configStore;
    private final QaSlideRepository qaSlideRepository;
    private final NotificationService notificationService;

    public QaSlideService(RedisEntityStore redisStore, ConfigStore configStore, QaSlideRepository qaSlideRepository, NotificationService notificationService) {
        this.redisStore = redisStore;
        this.configStore = configStore;
        this.qaSlideRepository = qaSlideRepository;
        this.notificationService = notificationService;
    }

    public Flux<QaSlide> listAll() {

        return redisStore.findByPatternWithFallback(SLIDE_BARCODE_PREFIX + ":all", qaSlide -> true, qaSlideRepository::findAll, QaSlide::barcode, QaSlide.class)
                .flatMapMany(Flux::fromIterable)
                .map(qaSlide -> new QaSlide(null, qaSlide.barcode(), EncryptionUtils.decrypt(qaSlide.activationCode())))
                .doOnSubscribe(sub -> log.debug("Initiating listAll operation"))
                .doOnComplete(() -> log.debug("Completed listAll operation"));
    }

    public Mono<QaSlide> create(QaSlide slide) {
        QaSlide qaSlide = new QaSlide(null, slide.barcode(), EncryptionUtils.encrypt(slide.activationCode()));
        return qaSlideRepository.save(qaSlide)
                .flatMap(saved -> redisStore.deleteKeysByPattern(SLIDE_BARCODE_PREFIX + "*")
                        .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_PATH_QA_SLIDE_BARCODE_PREFIX+"*"))
                        .thenReturn(new QaSlide(null, saved.barcode(), EncryptionUtils.decrypt(qaSlide.activationCode())))
                        .thenReturn(saved))
                .doOnSuccess(saved -> log.info("Slide created successfully with barcode: {}", saved.barcode()))
                .onErrorMap(DuplicateKeyException.class, ex -> {
                    log.warn("Duplicate barcode detected: {}", slide.barcode(), ex);
                    return new HttpRequestException(HttpStatus.CONFLICT, "Slide with barcode '" + slide.barcode() + "' already exists.");
                })
                .doOnError(e -> log.error("Failed to create slide with barcode {}: {}", slide.barcode(), e.getMessage(), e));
    }

    public Mono<QaSlide> updateByBarcode(String barcode, QaSlide slide) {

        if (!StringUtils.hasText(barcode)) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Barcode must not be blank."));
        }
        if (slide == null) {
            return Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Update payload must not be null."));
        }

        QaSlide patch = new QaSlide(null, null, EncryptionUtils.encrypt(slide.activationCode()));
        Query query = buildBarcodeQuery(barcode);
        return getByBarcode(barcode)
                .flatMap(oldData -> qaSlideRepository.findAndModify(query, patch)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException(ERROR_MSG + barcode)))
                        .flatMap(updated -> redisStore.deleteKeysByPattern(SLIDE_BARCODE_PREFIX + "*")
                                .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_PATH_QA_SLIDE_BARCODE_PREFIX + "*"))
                                .then(Mono.just(new QaSlide(null, updated.barcode(), EncryptionUtils.decrypt(updated.activationCode()))))
                                .flatMap(finalResult -> notificationService.notifyEntityChange("qaSlide", oldData, finalResult).thenReturn(finalResult))))
                .doOnSuccess(updated -> log.info("Slide updated successfully for barcode: {}", updated.barcode()))
                .doOnError(e -> log.error("Failed to update slide with barcode {}: {}", barcode, e.getMessage(), e));
    }

    public Mono<Boolean> deleteByBarcode(String barcode) {

        return qaSlideRepository.deleteByBarcode(barcode)
                .flatMap(count -> {
                    if (count == 0) {
                        return Mono.error(new ResourceNotFoundException(ERROR_MSG + barcode));
                    }
                    return redisStore.deleteKeysByPattern(SLIDE_BARCODE_PREFIX + "*")
                            .then(redisStore.deleteKeysByPattern(DICOM_RECEIVER_PATH_QA_SLIDE_BARCODE_PREFIX+"*"))
                            .thenReturn(true);
                })
                .doOnSuccess(v -> log.info("Slide deleted successfully with barcode: {}", barcode))
                .doOnError(e -> log.error("Failed to delete slide with barcode {}: {}", barcode, e.getMessage(), e));
    }

    public Mono<QaSlide> getByBarcode(String barcode) {

        String barcodeKey = SLIDE_BARCODE_PREFIX + barcode;
        return redisStore.findByKeyWithFallback(barcodeKey, () -> findByBarcode(barcode)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException(ERROR_MSG + barcode))), QaSlide.class)
                .map(qaSlide -> new QaSlide(null, qaSlide.barcode(), EncryptionUtils.decrypt(qaSlide.activationCode())))
                .doOnSuccess(slide -> log.info("Slide retrieved successfully for barcode: {}", barcode))
                .doOnError(e -> log.error("Failed to retrieve slide with barcode {}: {}", barcode, e.getMessage(), e));
    }

    private Mono<QaSlide> findByBarcode(String barcode) {
        return qaSlideRepository.findByBarcode(barcode);
    }

    private Query buildBarcodeQuery(String barcode) {
        return Query.query(Criteria.where("barcode").is(barcode));
    }

    public Mono<String> getPathQaDicomStoreUrl() {
        return configStore.get(PATH_QA_DICOM_STORE, DEFAULT_APPLICATION);
    }

    public Mono<QaSlideDetails> qaSlideDetails() {
        log.info("Fetching QA Slide Details..");
        return getPathQaDicomStoreUrl()
                .switchIfEmpty(Mono.error(new Exception("Dicom Web Url not found.")))
                .zipWith(listAll().collectList())
                .map(tuple -> new QaSlideDetails(tuple.getT1(), tuple.getT2()))
                .doOnError(error -> log.error("Failed to build QADetails", error));
    }

}