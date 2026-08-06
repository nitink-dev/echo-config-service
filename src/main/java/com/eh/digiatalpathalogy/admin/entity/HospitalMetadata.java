package com.eh.digiatalpathalogy.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.HOSPITAL_METADATA_COLLECTION;

@Document(collection = HOSPITAL_METADATA_COLLECTION)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HospitalMetadata(@Id String id, String type, List<String> value) {
}
