package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.SlideScanner;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface SlideScannerRepository extends ReactiveMongoRepository<SlideScanner, String>, BaseReactiveMongoRepository<SlideScanner> {

    Mono<SlideScanner> findByDeviceSerialNumber(String deviceSerialNumber);

    Mono<Long> deleteByDeviceSerialNumber(String deviceSerialNumber);
}
