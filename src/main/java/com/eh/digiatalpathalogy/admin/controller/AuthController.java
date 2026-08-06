package com.eh.digiatalpathalogy.admin.controller;

import com.eh.digiatalpathalogy.admin.model.RouteRuleDTO;
import com.eh.digiatalpathalogy.admin.model.auth.LoginRequest;
import com.eh.digiatalpathalogy.admin.model.auth.LoginResponse;
import com.eh.digiatalpathalogy.admin.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest req, ServerWebExchange exchange) {
        return service.login(req, exchange).map(ResponseEntity::ok);
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
        return service.logout(exchange).thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/config")
    public Flux<RouteRuleDTO> getAuthConfig() {
        return service.getAuthConfig();
    }

}
