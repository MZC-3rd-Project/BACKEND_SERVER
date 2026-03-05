package com.example.data.entity.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class TransactionRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceRoute forcedRoute = DataSourceRoutingContext.current();
        if (forcedRoute != null) {
            return forcedRoute;
        }

        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return DataSourceRoute.READ;
        }
        return DataSourceRoute.WRITE;
    }
}
