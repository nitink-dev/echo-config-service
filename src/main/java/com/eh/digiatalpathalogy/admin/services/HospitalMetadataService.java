package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.entity.HospitalMetadata;
import com.eh.digiatalpathalogy.admin.model.HospitalMetadataDTO;
import com.eh.digiatalpathalogy.admin.repository.HospitalMetadataRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.METADATA_HOSPITAL;

@Service
public class HospitalMetadataService {

    private static final Logger log = LoggerFactory.getLogger(HospitalMetadataService.class);

    private final RedisEntityStore redisStore;
    private final HospitalMetadataRepository hospitalMetadataRepository;

    public HospitalMetadataService(RedisEntityStore redisStore, HospitalMetadataRepository hospitalMetadataRepository) {
        this.redisStore = redisStore;
        this.hospitalMetadataRepository = hospitalMetadataRepository;
    }

    public Mono<HospitalMetadataDTO> getHospitalMetadata() {
        return redisStore.findByKey(METADATA_HOSPITAL, HospitalMetadataDTO.class)
                .switchIfEmpty(Mono.defer(this::fetchAndCache));
    }

    private Mono<HospitalMetadataDTO> fetchAndCache() {

        log.info("Cache miss :: fetching hospital metadata from DB.");
        Mono<List<String>> hospitalNames = hospitalMetadataRepository.findByType("hospital_name")
                .map(HospitalMetadata::value)
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<String>> locations = hospitalMetadataRepository.findByType("hospital_location")
                .map(HospitalMetadata::value)
                .defaultIfEmpty(Collections.emptyList());

        return Mono.zip(hospitalNames, locations)
                .map(tuple -> {
                    var distinctHospitalNames = new LinkedHashSet<>(tuple.getT1());
                    var distinctLocations = new LinkedHashSet<>(tuple.getT2());
                    return new HospitalMetadataDTO(List.copyOf(distinctHospitalNames), List.copyOf(distinctLocations)
                    );
                })
                .flatMap(metadata -> redisStore.save(METADATA_HOSPITAL, metadata)
                        .onErrorResume(e -> {
                            log.warn("Failed to cache hospital metadata", e);
                            return Mono.empty();
                        })
                        .thenReturn(metadata)
                );
    }

}
