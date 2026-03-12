package com.example.media.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaSchemaGuardrailsInitializerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void run_repairsOwnerTypeConstraintsForPostgreSql() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        MediaSchemaGuardrailsInitializer initializer = new MediaSchemaGuardrailsInitializer(dataSource, jdbcTemplate);

        initializer.run(applicationArguments);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).execute(sqlCaptor.capture());

        List<String> executedSql = sqlCaptor.getAllValues();
        assertThat(executedSql).hasSize(4);
        assertThat(executedSql.get(1)).contains("media_files_requested_owner_type_check");
        assertThat(executedSql.get(1)).contains("'STORE'");
        assertThat(executedSql.get(3)).contains("media_links_owner_type_check");
        assertThat(executedSql.get(3)).contains("'STORE'");
    }

    @Test
    void run_skipsConstraintRepairForNonPostgreSql() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");

        MediaSchemaGuardrailsInitializer initializer = new MediaSchemaGuardrailsInitializer(dataSource, jdbcTemplate);

        initializer.run(applicationArguments);

        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.anyString());
    }
}
