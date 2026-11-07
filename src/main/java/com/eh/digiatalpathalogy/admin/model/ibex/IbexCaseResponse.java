package com.eh.digiatalpathalogy.admin.model.ibex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IbexCaseResponse(String id, @JsonProperty("ibex_url") String ibexUrl) {
}
