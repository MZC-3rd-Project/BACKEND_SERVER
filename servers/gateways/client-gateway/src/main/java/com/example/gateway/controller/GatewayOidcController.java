package com.example.gateway.controller;

import com.example.gateway.config.GatewayOidcProperties;
import com.example.gateway.security.session.application.GatewayLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GatewayOidcController {

    private final GatewayLoginService loginService;
    private final GatewayOidcProperties oidcProperties;

    @GetMapping("/oauth2/authorization/keycloak")
    public Mono<ResponseEntity<Void>> authorize(@RequestParam(name = "redirect", required = false) String redirect) {
        return loginService.buildAuthorizationRedirect(redirect)
                .map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(uri)).build());
    }

    @GetMapping("/login/oauth2/code/keycloak")
    public Mono<ResponseEntity<Void>> callback(@RequestParam("code") String code,
                                               @RequestParam("state") String state,
                                               ServerWebExchange exchange) {
        return loginService.completeLogin(exchange, code, state)
                .map(redirectPath -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(redirectPath))
                        .<Void>build())
                .onErrorResume(error -> Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(oidcProperties.getLoginFailureUrl()))
                        .<Void>build()));
    }

    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> logout(ServerWebExchange exchange) {
        return loginService.logout(exchange)
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
    }
}
