package com.example.media.config;

import com.example.media.entity.MediaOwnerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaSchemaGuardrailsInitializer implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgreSql()) {
            log.debug("[MediaSchemaGuardrails] skip constraint repair. database is not PostgreSQL.");
            return;
        }

        repairOwnerTypeConstraint(
                "media_files",
                "media_files_requested_owner_type_check",
                "requested_owner_type",
                true
        );
        repairOwnerTypeConstraint(
                "media_links",
                "media_links_owner_type_check",
                "owner_type",
                false
        );
    }

    private boolean isPostgreSql() {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to inspect database metadata for media schema guardrails", exception);
        }
    }

    private void repairOwnerTypeConstraint(String tableName,
                                           String constraintName,
                                           String columnName,
                                           boolean nullable) {
        String allowedValues = Arrays.stream(MediaOwnerType.values())
                .map(MediaOwnerType::name)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));

        String predicate = nullable
                ? columnName + " IS NULL OR " + columnName + " IN (" + allowedValues + ")"
                : columnName + " IN (" + allowedValues + ")";

        jdbcTemplate.execute("ALTER TABLE IF EXISTS " + tableName
                + " DROP CONSTRAINT IF EXISTS " + constraintName);
        jdbcTemplate.execute("ALTER TABLE IF EXISTS " + tableName
                + " ADD CONSTRAINT " + constraintName + " CHECK (" + predicate + ")");

        log.info("[MediaSchemaGuardrails] repaired {}.{} with owner types: {}",
                tableName, constraintName, Arrays.toString(MediaOwnerType.values()));
    }
}
