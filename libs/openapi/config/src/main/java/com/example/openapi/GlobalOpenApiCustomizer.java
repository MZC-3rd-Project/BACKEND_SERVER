package com.example.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlobalOpenApiCustomizer implements OpenApiCustomizer {

    private static final String BEARER_SCHEME = "Bearer";
    private final OpenApiProperties properties;

    @Override
    public void customise(OpenAPI openApi) {
        // Info
        Info info = new Info()
                .title(properties.getTitle())
                .version(properties.getVersion())
                .description(properties.getDescription())
                .contact(new Contact()
                        .name(properties.getContact().getName())
                        .email(properties.getContact().getEmail())
                        .url(properties.getContact().getUrl()));
        openApi.info(info);

        // Security Scheme
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.components(components);
        }

        components.addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 인증 토큰"));

        openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));

        // Servers
        if (properties.getServers() != null && !properties.getServers().isEmpty()) {
            properties.getServers().forEach(server ->
                    openApi.addServersItem(new io.swagger.v3.oas.models.servers.Server()
                            .url(server.getUrl())
                            .description(server.getDescription())));
        }
    }
}
