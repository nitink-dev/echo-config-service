package com.eh.digiatalpathalogy.admin.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import static com.eh.digiatalpathalogy.admin.constant.DbCollections.AUTH_USER;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = AUTH_USER)
public record User(@Id String id, String username, String password) {
}
