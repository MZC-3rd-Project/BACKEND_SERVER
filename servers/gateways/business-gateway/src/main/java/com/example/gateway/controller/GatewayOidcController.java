package com.example.gateway.controller;

import com.example.gateway.config.BusinessGatewayOidcProperties;
import com.example.gateway.security.session.application.GatewayLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
    private final BusinessGatewayOidcProperties oidcProperties;

    @GetMapping("/oauth2/authorization/keycloak")
    public Mono<Void> authorize(@RequestParam(name = "redirect", required = false) String redirect,
                                ServerWebExchange exchange) {
        return loginService.buildAuthorizationRedirect(redirect)
                .flatMap(uri -> redirect(exchange.getResponse(), uri));
    }

    @GetMapping("/login/oauth2/code/keycloak")
    public Mono<Void> callback(@RequestParam("code") String code,
                               @RequestParam("state") String state,
                               ServerWebExchange exchange) {
        return loginService.completeLogin(exchange, code, state)
                .flatMap(redirectPath -> redirect(exchange.getResponse(), redirectPath))
                .onErrorResume(error -> redirect(exchange.getResponse(), oidcProperties.getLoginFailureUrl()));
    }

    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> logout(ServerWebExchange exchange) {
        return loginService.logout(exchange)
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
    }

    private Mono<Void> redirect(ServerHttpResponse response, String location) {
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        return response.setComplete();
    }
}
