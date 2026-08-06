package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.exception.HttpRequestException;
import com.eh.digiatalpathalogy.admin.exception.ResourceNotFoundException;
import com.eh.digiatalpathalogy.admin.services.QaSlideService;
import com.eh.digiatalpathalogy.admin.support.WebFluxControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static com.eh.digiatalpathalogy.admin.testdata.QaSlideTestData.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@WebFluxControllerTest(controllers = QaSlideController.class)
class QaSlideControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private QaSlideService qaSlideService;

    @Test
    @DisplayName("GET /api/slides → 200 returns QaSlideDetails")
    void qaSlideDetails_success() {

        given(qaSlideService.qaSlideDetails()).willReturn(Mono.just(qaSlideDetails()));
        webTestClient.get()
                .uri("/api/slides")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.dicomUrl").isEqualTo("https://dicom.qa/path")
                .jsonPath("$.qaSlides[0].barcode").isEqualTo("10224");
    }

    @Test
    @DisplayName("GET /api/slides/{barcode} → 200 returns QaSlide")
    void getByBarcode_success() {

        given(qaSlideService.getByBarcode("10224")).willReturn(Mono.just(pathQaSlide()));
        webTestClient.get()
                .uri("/api/slides/{barcode}", "10224")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.barcode").isEqualTo("10224")
                .jsonPath("$.activationCode").isEqualTo("Elcwq81cA1dPXm7M");
    }

    @Test
    @DisplayName("GET /api/slides/{barcode} → 404 with error envelope when slide is missing")
    void getByBarcode_notFound() {

        given(qaSlideService.getByBarcode(MISSING_BARCODE))
                .willReturn(Mono.error(new ResourceNotFoundException("Slide not found with barcode: " + MISSING_BARCODE)));

        webTestClient.get()
                .uri("/api/slides/{barcode}", MISSING_BARCODE)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Resource Not Found")
                .jsonPath("$.errorDescription").isEqualTo("Slide not found with barcode: " + MISSING_BARCODE);
    }

    @Test
    @DisplayName("POST /api/slides → 200 creates QaSlide")
    void create_success() {
        var request = newQaSlide();
        var saved = slide("id-100", request.barcode(), request.activationCode());

        given(qaSlideService.create(any(QaSlide.class))).willReturn(Mono.just(saved));

        webTestClient.post()
                .uri("/api/slides")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("id-100")
                .jsonPath("$.barcode").isEqualTo("10224")
                .jsonPath("$.activationCode").isEqualTo("Elcwq81cA1dPXm7M");
    }

    @Test
    @DisplayName("POST /api/slides → 409 when barcode already exists (conflict envelope)")
    void create_conflict() {
        var dup = pathQaSlide();
        given(qaSlideService.create(any(QaSlide.class))).willReturn(
                Mono.error(new HttpRequestException(
                        HttpStatus.CONFLICT,
                        "Slide with barcode '" + dup.barcode() + "' already exists."))
        );

        webTestClient.post()
                .uri("/api/slides")
                .bodyValue(dup)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Conflict")
                .jsonPath("$.errorDescription").isEqualTo("Slide with barcode '10224' already exists.");
    }

    @Test
    @DisplayName("POST /api/slides → 400 when activationCode is blank (validation)")
    void create_validationError() {
        var invalid = slide(null, "BC-VAL", "");

        webTestClient.post()
                .uri("/api/slides")
                .bodyValue(invalid)
                .exchange()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Validation Error");
    }

    @Test
    @DisplayName("PUT /api/slides/{barcode} → 200 updates QaSlide (activationCode)")
    void update_success() {
        String barcode = "10224";
        var req = slide(null, null, "new-activation-code");
        var resp = slide("id-333", barcode, "new-activation-code");

        given(qaSlideService.updateByBarcode(eq(barcode), any(QaSlide.class))).willReturn(Mono.just(resp));

        webTestClient.put()
                .uri("/api/slides/{barcode}", barcode)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("id-333")
                .jsonPath("$.barcode").isEqualTo("10224")
                .jsonPath("$.activationCode").isEqualTo("new-activation-code");
    }

    @Test
    @DisplayName("PUT /api/slides/{barcode} → 404 when barcode is blank ")
    void update_badRequest_barcodeBlank() {
        given(qaSlideService.updateByBarcode(eq(""), any(QaSlide.class)))
                .willReturn(Mono.error(new HttpRequestException(HttpStatus.BAD_REQUEST, "Barcode must not be blank.")));

        webTestClient.put()
                .uri("/api/slides/{barcode}", "")
                .bodyValue(slide(null, null, "ACT"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Not Found");
    }

    @Test
    @DisplayName("DELETE /api/slides/{id} → 200 true when deleted")
    void delete_success() {
        String barcode = "10224";
        given(qaSlideService.deleteByBarcode(barcode)).willReturn(Mono.just(true));

        webTestClient.delete()
                .uri("/api/slides/{id}", barcode)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("DELETE /api/slides/{id} → 404 when slide not found")
    void delete_notFound() {

        given(qaSlideService.deleteByBarcode(MISSING_BARCODE))
                .willReturn(Mono.error(new ResourceNotFoundException("Slide not found with barcode: " + MISSING_BARCODE)));

        webTestClient.delete()
                .uri("/api/slides/{id}", MISSING_BARCODE)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("Resource Not Found")
                .jsonPath("$.errorDescription").isEqualTo("Slide not found with barcode: " + MISSING_BARCODE);
    }
}
