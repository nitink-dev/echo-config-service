package com.eh.digiatalpathalogy.admin.services;

import com.eh.digiatalpathalogy.admin.config.ConfigStore;
import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.model.QaSlideDetails;
import com.eh.digiatalpathalogy.admin.repository.QaSlideRepository;
import com.eh.digiatalpathalogy.admin.util.EncryptionUtils;
import com.eh.digiatalpathalogy.admin.util.RedisEntityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static com.eh.digiatalpathalogy.admin.testdata.QaSlideTestData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QaSlideServiceTest {

    @Mock
    private RedisEntityStore redisStore;
    @Mock
    private ConfigStore configStore;
    @Mock
    private QaSlideRepository qaSlideRepository;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private QaSlideService service;

    private QaSlide slide(String id, String barcode, String activationCode) {
        return new QaSlide(id, barcode, activationCode);
    }

    @Test
    @DisplayName("listAll: returns all slides from Redis (no fallback) with unmasked activation codes")
    void listAll_success() {
        var slides = listPathQaSlide();

        when(redisStore.findByPatternWithFallback(anyString(), any(), any(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.just(slides));

        StepVerifier.create(service.listAll())
                .expectNextMatches(q -> q.barcode().equals(slides.get(0).barcode())
                        && q.activationCode().equals(slides.get(0).activationCode()))
                .expectNextCount(1)
                .verifyComplete();

        verify(redisStore).findByPatternWithFallback(anyString(), any(), any(), any(), eq(QaSlide.class));
        verifyNoInteractions(qaSlideRepository);
    }

    @Test
    @DisplayName("create: inserts a slide, invalidates caches and triggers an email")
    void create_success() {
        var request = newQaSlide();
        var saved = slide("id-100", request.barcode(), "Vsy6H0mbnuedkVATRrmhkji/DneagLfZEACPiNquNjOQQbRYLfdjGFYnVss=");

        when(qaSlideRepository.save(any(QaSlide.class))).thenReturn(Mono.just(saved));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("qaSlide"), isNull(), eq(saved))).thenReturn(Mono.empty());

        try (MockedStatic<EncryptionUtils> mocked = mockStatic(EncryptionUtils.class)) {
            mocked.when(() -> EncryptionUtils.encrypt(anyString())).thenReturn(saved.activationCode());
            mocked.when(() -> EncryptionUtils.decrypt(anyString())).thenReturn(request.activationCode());

            StepVerifier.create(service.create(request))
                    .expectNext(saved)
                    .verifyComplete();
        }

        verify(redisStore,times(2)).deleteKeysByPattern(anyString());
        verify(notificationService).notifyEntityChange(eq("qaSlide"), isNull(), eq(saved));
    }

    @Test
    @DisplayName("create: DuplicateKeyException → HttpRequestException(409, exact message)")
    void create_duplicateBarcode_conflict() {
        var request = slide(null, "10224", "test-activation-code");
        when(qaSlideRepository.save(any(QaSlide.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("E11000 duplicate key")));

        try (MockedStatic<EncryptionUtils> mocked = mockStatic(EncryptionUtils.class)) {
            mocked.when(() -> EncryptionUtils.encrypt(anyString())).thenReturn("encrypted-value");

            StepVerifier.create(service.create(request))
                    .expectErrorSatisfies(ex -> {
                        assert ex instanceof HttpRequestException;
                        var hre = (HttpRequestException) ex;
                        assert hre.getStatus() == HttpStatus.CONFLICT;
                        assert ("Slide with barcode '" + request.barcode() + "' already exists.").contains(hre.getResponseBody());
                    })
                    .verify();
        }

        verify(qaSlideRepository).save(any(QaSlide.class));
        verify(redisStore, never()).deleteKeysByPattern(anyString());
        verify(redisStore, never()).deleteByKey(anyString());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("updateByBarcode: 400 when barcode is blank")
    void update_blankBarcode_badRequest() {
        StepVerifier.create(service.updateByBarcode("", slide(null, null, "new-activation-code")))
                .expectErrorSatisfies(ex -> {
                    assert ex instanceof HttpRequestException;
                    var hre = (HttpRequestException) ex;
                    assert hre.getStatus() == HttpStatus.BAD_REQUEST;
                    assert "Barcode must not be blank.".contains(hre.getResponseBody());
                })
                .verify();

        verifyNoInteractions(qaSlideRepository, redisStore);
    }

    @Test
    @DisplayName("updateByBarcode: 400 when payload is null")
    void update_nullPayload_badRequest() {
        StepVerifier.create(service.updateByBarcode("10224", null))
                .expectErrorSatisfies(ex -> {
                    assert ex instanceof HttpRequestException;
                    var hre = (HttpRequestException) ex;
                    assert hre.getStatus() == HttpStatus.BAD_REQUEST;
                    assert "Update payload must not be null.".contains(hre.getResponseBody());
                })
                .verify();

        verifyNoInteractions(qaSlideRepository, redisStore);
    }

    @Test
    @DisplayName("updateByBarcode: updates activationCode, invalidates caches, triggers an email and returns the new activation code in plaintext")
    void update_success() {
        String bc = "10224";
        var patchFromRequest = slide(null, null, "Elcwq81cA1dPXm7M");
        var oldPersisted = slide("id-000", bc, "old-cipher-text");
        var updated = slide("id-333", bc, "Vsy6H0mbnuedkVATRrmhkji/DneagLfZEACPiNquNjOQQbRYLfdjGFYnVss=");
        String newPlainActivationCode = "Elcwq81cA1dPXm7M-new";

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class))).thenReturn(Mono.just(oldPersisted));
        when(qaSlideRepository.findAndModify(any(), any(QaSlide.class)))
                .thenReturn(Mono.just(updated));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("qaSlide"), any(QaSlide.class), any(QaSlide.class)))
                .thenReturn(Mono.empty());

        try (MockedStatic<EncryptionUtils> mocked = mockStatic(EncryptionUtils.class)) {
            mocked.when(() -> EncryptionUtils.encrypt(anyString())).thenReturn("new-cipher-text");
            mocked.when(() -> EncryptionUtils.mask(anyString())).thenCallRealMethod();
            mocked.when(() -> EncryptionUtils.decrypt(updated.activationCode())).thenReturn(newPlainActivationCode);

            StepVerifier.create(service.updateByBarcode(bc, patchFromRequest))
                    .expectNextMatches(result -> result.id() == null && bc.equals(result.barcode())
                            && newPlainActivationCode.equals(result.activationCode()))
                    .verifyComplete();
        }

        verify(qaSlideRepository).findAndModify(any(), any(QaSlide.class));
        verify(redisStore,times(2)).deleteKeysByPattern(anyString());
        verify(notificationService).notifyEntityChange(eq("qaSlide"), any(QaSlide.class),
                argThat((QaSlide n) -> newPlainActivationCode.equals(n.activationCode())));
    }

    @Test
    @DisplayName("updateByBarcode: 404 when slide not found")
    void update_notFound() {

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.just(slide("id-000", MISSING_BARCODE, "old-cipher-text")));
        when(qaSlideRepository.findAndModify(any(), any(QaSlide.class))).thenReturn(Mono.empty());

        try (MockedStatic<EncryptionUtils> mocked = mockStatic(EncryptionUtils.class)) {
            mocked.when(() -> EncryptionUtils.encrypt(anyString())).thenReturn("new-cipher-text");
            mocked.when(() -> EncryptionUtils.mask(anyString())).thenCallRealMethod();

            StepVerifier.create(service.updateByBarcode(MISSING_BARCODE, slide(null, null, "new-activation-code")))
                    .expectErrorSatisfies(ex -> {
                        assert ex instanceof ResourceNotFoundException;
                        assert ("Slide not found with barcode: " + MISSING_BARCODE).equals(ex.getMessage());
                    })
                    .verify();
        }

        verify(qaSlideRepository).findAndModify(any(), any(QaSlide.class));
        verify(redisStore, never()).deleteKeysByPattern(anyString());
        verify(redisStore, never()).deleteByKey(anyString());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("deleteByBarcode: returns true, invalidates caches and triggers an email")
    void delete_success() {
        String bc = "BC-DEL";
        var oldPersisted = slide("id-000", bc, "old-cipher-text");
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class))).thenReturn(Mono.just(oldPersisted));
        when(qaSlideRepository.deleteByBarcode(bc)).thenReturn(Mono.just(1L));
        when(redisStore.deleteKeysByPattern(anyString())).thenReturn(Mono.empty());
        when(notificationService.notifyEntityChange(eq("qaSlide"), any(QaSlide.class), isNull())).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteByBarcode(bc))
                .expectNext(true)
                .verifyComplete();

        verify(redisStore,times(2)).deleteKeysByPattern(anyString());
        verify(notificationService).notifyEntityChange(eq("qaSlide"), any(QaSlide.class), isNull());
    }

    @Test
    @DisplayName("deleteByBarcode: 404 when slide not found")
    void delete_notFound() {

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Slide not found with barcode: " + MISSING_BARCODE)));

        StepVerifier.create(service.deleteByBarcode(MISSING_BARCODE))
                .expectErrorSatisfies(ex -> {
                    assert ex instanceof ResourceNotFoundException;
                    assert ("Slide not found with barcode: " + MISSING_BARCODE).equals(ex.getMessage());
                })
                .verify();

        verify(qaSlideRepository, never()).deleteByBarcode(anyString());
        verify(redisStore, never()).deleteKeysByPattern(anyString());
        verify(redisStore, never()).deleteByKey(anyString());
        verifyNoInteractions(notificationService);
    }

//    @Test
//    @DisplayName("getByBarcode: returns slide from Redis (or fallback)")
//    void getByBarcode_success() {
//
//        var qaSlide = new QaSlide(UUID.randomUUID().toString(), "10224", "Elcwq81cA1dPXm7M");
//        mockStatic(EncryptionUtils.class).when(() -> EncryptionUtils.decrypt(anyString())).thenReturn(anyString());
//        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class)))
//                .thenReturn(Mono.just(qaSlide));
//
//        StepVerifier.create(service.getByBarcode("10224"))
//                .expectNext(qaSlide)
//                .verifyComplete();
//
//        verify(redisStore).findByKeyWithFallback(anyString(), any(), eq(QaSlide.class));
//    }

    @Test
    @DisplayName("getByBarcode: returns slide from Redis (or fallback) with an unmasked activation code")
    void getByBarcode_success() {

        var qaSlide = pathQaSlide();

        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class))).thenReturn(Mono.just(qaSlide));

        StepVerifier.create(service.getByBarcode("10224"))
                .expectNextMatches(result -> result != null && result.id() == null && result.barcode().equals("10224")
                        && result.activationCode().equals(qaSlide.activationCode()))
                .verifyComplete();

        verify(redisStore).findByKeyWithFallback(anyString(), any(), eq(QaSlide.class));
        verifyNoInteractions(notificationService);
    }


    @Test
    @DisplayName("getByBarcode: 404 when missing")
    void getByBarcode_notFound() {
        String barcode = MISSING_BARCODE;
        when(redisStore.findByKeyWithFallback(anyString(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.error(new ResourceNotFoundException("Slide not found with barcode: " + barcode)));

        StepVerifier.create(service.getByBarcode(barcode))
                .expectErrorSatisfies(ex -> {
                    assert ex instanceof ResourceNotFoundException;
                    assert ("Slide not found with barcode: " + barcode).equals(ex.getMessage());
                })
                .verify();
    }

    @Test
    @DisplayName("qaSlideDetails: zips DICOM store URL with listAll slides")
    void qaSlideDetails_success() {
        var url = "https://dicom.qa/path";
        var slides = listPathQaSlide();

        when(configStore.get(anyString(), anyString())).thenReturn(Mono.just(url));
        when(redisStore.findByPatternWithFallback(anyString(), any(), any(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.just(slides));

        StepVerifier.create(service.qaSlideDetails())
                .expectNextMatches(details -> url.equals(details.getDicomUrl()) && !details.getQaSlides().isEmpty() &&
                        "10224".equals(details.getQaSlides().get(0).barcode()))
                .verifyComplete();

        verify(configStore).get(anyString(), anyString());
    }

    @Test
    @DisplayName("qaSlideDetails: 500 when DICOM store URL is missing")
    void qaSlideDetails_missingUrl_error() {
        var slides = listPathQaSlide();
        when(configStore.get(anyString(), anyString())).thenReturn(Mono.empty());
        when(redisStore.findByPatternWithFallback(anyString(), any(), any(), any(), eq(QaSlide.class)))
                .thenReturn(Mono.just(slides));

        StepVerifier.create(service.qaSlideDetails())
                .expectErrorSatisfies(ex -> {
                    assert ex instanceof Exception;
                    assert "Dicom Web Url not found.".equals(ex.getMessage());
                })
                .verify();
    }

    @Test
    @DisplayName("Getter & Setter test for QaSlideDetails")
    void getterSetterTest() {

        QaSlide slide1 = new QaSlide(UUID.randomUUID().toString(), "10224", "test-activation");
        QaSlide slide2 = new QaSlide(UUID.randomUUID().toString(), "10237", "test-activation");
        QaSlide slide3 = new QaSlide(UUID.randomUUID().toString(), "test123456", "test-activation");


        String dicomUrl = "https://dicom.qa/path";
        List<QaSlide> slides = List.of(slide1, slide2, slide3);

        QaSlideDetails dto = new QaSlideDetails(null, null);
        dto.setDicomUrl(dicomUrl);
        dto.setQaSlides(slides);

        assertEquals(dicomUrl, dto.getDicomUrl());
        assertEquals(slides, dto.getQaSlides());
    }

    @Test
    @DisplayName("All-args constructor should assign values correctly")
    void allArgsConstructorTest() {

        QaSlide slide1 = new QaSlide(UUID.randomUUID().toString(), "10224", "test-activation");
        QaSlide slide2 = new QaSlide(UUID.randomUUID().toString(), "10237", "test-activation");
        QaSlide slide3 = new QaSlide(UUID.randomUUID().toString(), "test123456", "test-activation");

        String dicomUrl = "https://dicom.qa/path";
        List<QaSlide> slides = List.of(slide1, slide2, slide3);
        QaSlideDetails dto = new QaSlideDetails(dicomUrl, slides);

        assertEquals(dicomUrl, dto.getDicomUrl());
        assertEquals(slides, dto.getQaSlides());
    }
}