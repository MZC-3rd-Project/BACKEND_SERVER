package com.example.clients.product.config;

import com.example.clients.product.facade.ProductClientFacade;
import com.example.clients.product.facade.ProductEndingSoonClientFacade;
import com.example.clients.product.facade.ProductItemQueryClientFacade;
import com.example.clients.product.facade.ProductItemSummaryClientFacade;
import com.example.clients.product.impl.DefaultProductClientFacade;
import com.example.config.webclient.WebClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebClientAutoConfiguration.class,
                    ProductClientAutoConfiguration.class
            ));

    @Test
    void registersProductClientFacadeBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DefaultProductClientFacade.class);
            assertThat(context).hasSingleBean(ProductClientFacade.class);
            assertThat(context).hasSingleBean(ProductItemSummaryClientFacade.class);
            assertThat(context).hasSingleBean(ProductItemQueryClientFacade.class);
            assertThat(context).hasSingleBean(ProductEndingSoonClientFacade.class);

            DefaultProductClientFacade delegate = context.getBean(DefaultProductClientFacade.class);
            assertThat(context.getBean(ProductClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(ProductItemSummaryClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(ProductItemQueryClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(ProductEndingSoonClientFacade.class)).isSameAs(delegate);
        });
    }
}
