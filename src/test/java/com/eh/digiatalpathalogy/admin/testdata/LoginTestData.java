package com.eh.digiatalpathalogy.admin.testdata;

import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.model.auth.LoginRequest;
import com.eh.digiatalpathalogy.admin.model.auth.LoginResponse;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LoginTestData {

    public static LoginRequest loginRequest() {
        return new LoginRequest("dev-user", "Admin@123");
    }

    public static LoginResponse loginResponse() {
        return new LoginResponse("dev-user","Dev User", List.of("ROLE_ADMIN"), List.of("platform.read", "platform.update"),30L);}

    public static SecurityPolicyConfig securityPolicyConfig() {
        return new SecurityPolicyConfig(
                Map.of("eh-admin", "ROLE_ADMIN", "eh-pathologist", "ROLE_PATHOLOGIST"),
                Map.of(
                        "ROLE_ADMIN", List.of("platform.read", "platform.update", "platform.delete"),
                        "ROLE_PATHOLOGIST", List.of("platform.read")
                ),
                new SecurityPolicyConfig.SessionPolicy(Duration.ofMinutes(20)),
                List.of("platform.admin"), // super scopes
                new SecurityPolicyConfig.Routes(
                        List.of(
                                new SecurityPolicyConfig.RouteRule(
                                        "/api/public/**", List.of(HttpMethod.GET, HttpMethod.POST), true, List.of()),
                                new SecurityPolicyConfig.RouteRule("/api/secure/**", List.of(HttpMethod.GET), false, List.of("platform.read"))
                        )
                )
        );
    }
}
