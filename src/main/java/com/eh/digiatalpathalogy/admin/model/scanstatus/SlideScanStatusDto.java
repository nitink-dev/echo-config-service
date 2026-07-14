package com.eh.digiatalpathalogy.admin.model.scanstatus;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashSet;
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlideScanStatusDto(String id, String accessionNumber, String slideBarcode, String seriesId,
                                 String deviceSerialNumber,
                                 String scanStatus, Double progressPercent,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
                                 @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt,
                                 LinkedHashSet<ScanStatus> progressEvents) {
}
