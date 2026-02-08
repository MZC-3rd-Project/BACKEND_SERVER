package com.example.openapi;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.openapi")
public class OpenApiProperties {

    private boolean enabled = true;
    private String title = "Project03 API";
    private String version = "1.0.0";
    private String description = "Project03 Backend API Documentation";
    private Contact contact = new Contact();
    private List<Server> servers = new ArrayList<>();

    @Getter
    @Setter
    public static class Contact {
        private String name = "Project03 Team";
        private String email = "";
        private String url = "";
    }

    @Getter
    @Setter
    public static class Server {
        private String url;
        private String description;
    }
}
