package com.example.gateway.config;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class GatewayLoginSuccessController {

    @GetMapping(value = "/login/success", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> loginSuccess(Mono<Principal> principalMono) {
        return principalMono
                .cast(Authentication.class)
                .map(authentication -> Map.<String, Object>of(
                        "success", true,
                        "authenticated", authentication.isAuthenticated(),
                        "name", authentication.getName(),
                        "roles", extractRoles(authentication),
                        "claims", extractClaims(authentication)
                ))
                .defaultIfEmpty(Map.of(
                        "success", false,
                        "authenticated", false
                ));
    }

    private List<String> extractRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Map<String, Object> extractClaims(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getClaims();
        }
        return Map.of();
    }
}
