package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.entity.HospitalMetadata;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface HospitalMetadataRepository extends ReactiveMongoRepository<HospitalMetadata, String>, BaseReactiveMongoRepository<HospitalMetadata> {
    Mono<HospitalMetadata> findByType(String type);
}
