package com.eh.digiatalpathalogy.admin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HostInfo(String ipAddress, Integer port, String serviceName, String displayName) {
}
