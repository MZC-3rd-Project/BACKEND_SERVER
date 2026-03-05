package com.example.clients.stock.config;

import com.example.clients.stock.facade.StockAvailabilityQueryFacade;
import com.example.clients.stock.facade.StockClientFacade;
import com.example.clients.stock.facade.StockInfoQueryClientFacade;
import com.example.clients.stock.facade.StockItemReferenceQueryFacade;
import com.example.clients.stock.facade.StockReservationClientFacade;
import com.example.clients.stock.impl.DefaultStockClientFacade;
import com.example.config.webclient.WebClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StockClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    WebClientAutoConfiguration.class,
                    StockClientAutoConfiguration.class
            ));

    @Test
    void registersStockClientFacadeBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DefaultStockClientFacade.class);
            assertThat(context).hasSingleBean(StockClientFacade.class);
            assertThat(context).hasSingleBean(StockReservationClientFacade.class);
            assertThat(context).hasSingleBean(StockItemReferenceQueryFacade.class);
            assertThat(context).hasSingleBean(StockInfoQueryClientFacade.class);
            assertThat(context).hasSingleBean(StockAvailabilityQueryFacade.class);

            DefaultStockClientFacade delegate = context.getBean(DefaultStockClientFacade.class);
            assertThat(context.getBean(StockClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(StockReservationClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(StockItemReferenceQueryFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(StockInfoQueryClientFacade.class)).isSameAs(delegate);
            assertThat(context.getBean(StockAvailabilityQueryFacade.class)).isSameAs(delegate);
        });
    }
}
