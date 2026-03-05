package com.example.profile.crypto;

import com.example.security.crypto.encryption.Encryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.security.crypto.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProfileSensitiveDataEncryptionMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Encryptor encryptor;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int migratedEmailCount = migrateColumn("email");
        int migratedPhoneNumberCount = migrateColumn("phone_number");

        if (migratedEmailCount > 0 || migratedPhoneNumberCount > 0) {
            log.info("[ProfileSensitiveDataMigration] completed. email={}, phoneNumber={}",
                    migratedEmailCount, migratedPhoneNumberCount);
            return;
        }

        // Legacy schema fallback.
        int migratedLegacyPhoneCount = migrateColumn("phone");
        if (migratedLegacyPhoneCount > 0) {
            log.info("[ProfileSensitiveDataMigration] completed with legacy phone column. phone={}",
                    migratedLegacyPhoneCount);
        } else {
            log.debug("[ProfileSensitiveDataMigration] no plaintext sensitive data found");
        }
    }

    private int migrateColumn(String columnName) {
        if (!columnExists("profiles", columnName)) {
            return 0;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT user_id, " + columnName + " AS value " +
                        "FROM profiles WHERE " + columnName + " IS NOT NULL"
        );

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get("user_id"));
            String currentValue = row.get("value") == null ? null : String.valueOf(row.get("value"));
            if (userId == null || !StringUtils.hasText(currentValue) || EncryptedStringAttributeConverter.isEncrypted(currentValue)) {
                continue;
            }

            String encrypted = EncryptedStringAttributeConverter.ENCRYPTED_PREFIX + encryptor.encrypt(currentValue);
            int updated = jdbcTemplate.update(
                    "UPDATE profiles SET " + columnName + " = ? WHERE user_id = ?",
                    encrypted,
                    userId
            );
            if (updated > 0) {
                migrated++;
            }
        }
        return migrated;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
