package com.eh.digiatalpathalogy.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.QA_SLIDE;

@Document(collection = QA_SLIDE)
@JsonIgnoreProperties(ignoreUnknown = true)
public record QaSlide(@Id String id, String barcode, @NotBlank String activationCode) {
}
