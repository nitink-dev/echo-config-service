package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.SlideScanStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface SlideScanStatusRepository extends ReactiveMongoRepository<SlideScanStatus, String>, BaseReactiveMongoRepository<SlideScanStatus> {

    Flux<SlideScanStatus> findByScanStatus(String scanStatus, Pageable pageable);

    Mono<Long> countByScanStatus(String scanStatus);

    Flux<SlideScanStatus> findByScanStatusNotIn(Collection<String> excludedStatuses, Pageable pageable);

    Flux<SlideScanStatus> findByScanStatusIn(Collection<String> includedStatuses, Pageable pageable);

    Mono<Long> countByScanStatusNotIn(Collection<String> excludedStatuses);

    Mono<Long> countByScanStatusIn(Collection<String> includedStatuses);

    Mono<SlideScanStatus> findBySlideBarcode(String slideBarcode);

    @Aggregation(pipeline = {
            "{ $group: { _id: '$slideBarcode' } }",
            "{ $project: { _id: 0, slideBarcode: '$_id' } }"
    })
    Flux<String> findAllSlideBarcodes();


}
