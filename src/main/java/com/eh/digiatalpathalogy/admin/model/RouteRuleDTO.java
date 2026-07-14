package com.eh.digiatalpathalogy.admin.model;

import java.util.List;

public record RouteRuleDTO(String api, List<String> methods, boolean isPublic, List<String> requiredScopes) {
}