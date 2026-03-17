package com.example.profile.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.profile.dummy-bootstrap")
public class ProfileDummyBootstrapProperties {

    private boolean enabled = false;
    private Long userId = 9000001L;
    private String email;
    private String nickname;
}
