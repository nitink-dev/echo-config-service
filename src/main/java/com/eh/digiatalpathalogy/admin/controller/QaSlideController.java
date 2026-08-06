package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;
import com.eh.digiatalpathalogy.admin.model.QaSlideDetails;
import com.eh.digiatalpathalogy.admin.services.QaSlideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "api/slides")
public class QaSlideController {

    private final QaSlideService qaSlideService;

    public QaSlideController(QaSlideService qaSlideService) {
        this.qaSlideService = qaSlideService;
    }

    @GetMapping
    public Mono<QaSlideDetails> qaSlideDetails() {
        return qaSlideService.qaSlideDetails();
    }

    @GetMapping("/{barcode}")
    public Mono<QaSlide> getByBarcode(@PathVariable String barcode) {
        return qaSlideService.getByBarcode(barcode);
    }

    @PostMapping
    public Mono<QaSlide> create(@Valid @RequestBody QaSlide slide) {
        return qaSlideService.create(slide);
    }

    @PutMapping("/{barcode}")
    public Mono<QaSlide> updateByBarcode(@PathVariable  @NotBlank(message = "barcode must not be blank") String barcode, @Valid @RequestBody QaSlide slide) {
        return qaSlideService.updateByBarcode(barcode, slide);
    }

    @DeleteMapping("/{id}")
    public Mono<Boolean> delete(@PathVariable String id) {
        return qaSlideService.deleteByBarcode(id);
    }

}
