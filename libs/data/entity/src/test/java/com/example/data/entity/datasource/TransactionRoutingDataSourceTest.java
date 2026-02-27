package com.example.data.entity.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionRoutingDataSourceTest {

    private final ExposedTransactionRoutingDataSource routingDataSource = new ExposedTransactionRoutingDataSource();

    @AfterEach
    void tearDown() {
        while (DataSourceRoutingContext.current() != null) {
            DataSourceRoutingContext.pop();
        }
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void defaultsToWriteRoute() {
        assertThat(routingDataSource.currentRoute()).isEqualTo(DataSourceRoute.WRITE);
    }

    @Test
    void readOnlyTransactionUsesReadRoute() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(routingDataSource.currentRoute()).isEqualTo(DataSourceRoute.READ);
    }

    @Test
    void useWriteAnnotationContextOverridesReadOnlyTransaction() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        DataSourceRoutingContext.push(DataSourceRoute.WRITE);

        assertThat(routingDataSource.currentRoute()).isEqualTo(DataSourceRoute.WRITE);
    }

    private static final class ExposedTransactionRoutingDataSource extends TransactionRoutingDataSource {
        Object currentRoute() {
            return determineCurrentLookupKey();
        }
    }
}
