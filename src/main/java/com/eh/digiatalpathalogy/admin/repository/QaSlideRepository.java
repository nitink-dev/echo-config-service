package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface QaSlideRepository extends ReactiveMongoRepository<QaSlide, String>, BaseReactiveMongoRepository<QaSlide> {
    Mono<QaSlide> findByBarcode(String barcode);

    Mono<Long> deleteByBarcode(String barcode);
}
