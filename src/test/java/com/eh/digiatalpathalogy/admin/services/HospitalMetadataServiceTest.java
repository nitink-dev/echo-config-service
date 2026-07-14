package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.entity.HospitalMetadata;
import com.eh.digiatalpathalogy.admin.model.HospitalMetadataDTO;
import com.eh.digiatalpathalogy.admin.repository.HospitalMetadataRepository;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.eh.digiatalpathalogy.admin.constant.RedisCacheKey.METADATA_HOSPITAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalMetadataServiceTest {

    @Mock
    private RedisEntityStore redisStore;

    @Mock
    private HospitalMetadataRepository hospitalMetadataRepository;

    @InjectMocks
    private HospitalMetadataService service;

    @Test
    @DisplayName("getHospitalMetadata: return from cached")
    void getHospitalMetadata() {

        HospitalMetadataDTO cached = new HospitalMetadataDTO(
                List.of("Endeavor Hospital"),
                List.of("Central Rd., Arlington Heights", "75th Street, Naperville")
        );

        when(redisStore.findByKey(anyString(), eq(HospitalMetadataDTO.class))).thenReturn(Mono.just(cached));

        StepVerifier.create(service.getHospitalMetadata())
                .expectNext(cached)
                .verifyComplete();

        verify(redisStore, times(1)).findByKey(anyString(), eq(HospitalMetadataDTO.class));
        verifyNoInteractions(hospitalMetadataRepository);
        verify(redisStore, never()).save(anyString(), any());
    }


    @Test
    @DisplayName("getHospitalMetadata: cache miss should fetch from db and save to redis and return")
    void getHospitalMetadata_cacheMiss() {

        when(redisStore.findByKey(METADATA_HOSPITAL, HospitalMetadataDTO.class)).thenReturn(Mono.empty());

        HospitalMetadata namesDoc = new HospitalMetadata(
                "1",
                "hospital_name",
                List.of("Endeavor Hospital", "Endeavor Hospital")
        );

        HospitalMetadata locDoc = new HospitalMetadata(
                "2",
                "hospital_location",
                List.of("Central Rd., Arlington Heights", "75th Street, Naperville")
        );

        when(hospitalMetadataRepository.findByType("hospital_name")).thenReturn(Mono.just(namesDoc));
        when(hospitalMetadataRepository.findByType("hospital_location")).thenReturn(Mono.just(locDoc));
        when(redisStore.save(anyString(), any(HospitalMetadataDTO.class))).thenReturn(Mono.empty());

        StepVerifier.create(service.getHospitalMetadata())
                .assertNext(dto -> {
                    assertEquals(List.of("Endeavor Hospital"), dto.getLocations());
                    assertEquals(List.of("Central Rd., Arlington Heights", "75th Street, Naperville"), dto.getNames());
                })
                .verifyComplete();

        verify(redisStore).findByKey(anyString(), eq(HospitalMetadataDTO.class));
        verify(hospitalMetadataRepository).findByType("hospital_name");
        verify(hospitalMetadataRepository).findByType("hospital_location");
        verify(redisStore).save(eq(METADATA_HOSPITAL), any(HospitalMetadataDTO.class));
    }

    @Test
    @DisplayName("getHospitalMetadata: cache miss should fetch from db and save failed to redis and return")
    void getHospitalMetadata_cacheMiss_whenSaveFails_shouldStillReturnMetadata() {

        when(redisStore.findByKey(METADATA_HOSPITAL, HospitalMetadataDTO.class)).thenReturn(Mono.empty());

        HospitalMetadata namesDoc = new HospitalMetadata(
                "1",
                "hospital_name",
                List.of("Endeavor Hospital")
        );

        HospitalMetadata locDoc = new HospitalMetadata(
                "2",
                "hospital_location",
                List.of("Central Rd., Arlington Heights", "75th Street, Naperville")
        );

        when(hospitalMetadataRepository.findByType("hospital_name")).thenReturn(Mono.just(namesDoc));
        when(hospitalMetadataRepository.findByType("hospital_location")).thenReturn(Mono.just(locDoc));
        when(redisStore.save(eq(METADATA_HOSPITAL), any(HospitalMetadataDTO.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis down")));

        StepVerifier.create(service.getHospitalMetadata())
                .assertNext(dto -> {
                    assertEquals(List.of("Endeavor Hospital"), dto.getLocations());
                    assertEquals(List.of("Central Rd., Arlington Heights", "75th Street, Naperville"), dto.getNames());
                })
                .verifyComplete();

        verify(redisStore).save(anyString(), any(HospitalMetadataDTO.class));
    }
}
