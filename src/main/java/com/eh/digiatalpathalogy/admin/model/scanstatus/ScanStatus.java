package com.eh.digiatalpathalogy.admin.model.scanstatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanStatus(String type, String scanStatus, String message) {
}
