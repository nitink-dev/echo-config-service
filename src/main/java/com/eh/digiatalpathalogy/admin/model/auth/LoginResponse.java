package com.eh.digiatalpathalogy.admin.model.auth;

import java.io.Serializable;
import java.util.List;

public record LoginResponse(String username, String displayName, List<String> roles, List<String> scopes,
                            Long sessionTimeoutMinutes) implements Serializable {
}
