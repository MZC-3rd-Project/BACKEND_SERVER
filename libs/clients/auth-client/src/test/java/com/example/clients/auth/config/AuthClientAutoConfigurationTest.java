package com.example.clients.auth.config;

import com.example.clients.auth.facade.AuthItemQueryClientFacade;
import com.example.clients.auth.facade.AuthItemSummaryClientFacade;
import com.example.clients.auth.facade.DefaultAuthClientFacade;
import com.example.config.webclient.WebClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebClientAutoConfiguration.class,
                    AuthClientAutoConfiguration.class
            ));

    @Test
    void registersAuthClientFacadeBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DefaultAuthClientFacade.class);
            assertThat(context).hasSingleBean(AuthItemSummaryClientFacade.class);
            assertThat(context).hasSingleBean(AuthItemQueryClientFacade.class);

            DefaultAuthClientFacade delegate = context.getBean(DefaultAuthClientFacade.class);
            assertThat(context.getBean(AuthItemSummaryClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(AuthItemQueryClientFacade.class)).isSameAs(delegate);
        });
    }
}
