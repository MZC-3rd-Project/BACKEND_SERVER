package com.example.gateway.security.session.application;

import com.example.gateway.config.GatewaySessionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRefreshTokenHasherTest {

    @Test
    void hash_returnsDeterministicHashedValue() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        properties.setRefreshTokenHashPepper("pepper-A");
        GatewayRefreshTokenHasher hasher = new GatewayRefreshTokenHasher(properties);

        String hash1 = hasher.hash("refresh-token");
        String hash2 = hasher.hash("refresh-token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo("refresh-token");
    }

    @Test
    void hash_changesWhenPepperChanges() {
        GatewaySessionProperties propertiesA = new GatewaySessionProperties();
        propertiesA.setRefreshTokenHashPepper("pepper-A");
        GatewayRefreshTokenHasher hasherA = new GatewayRefreshTokenHasher(propertiesA);

        GatewaySessionProperties propertiesB = new GatewaySessionProperties();
        propertiesB.setRefreshTokenHashPepper("pepper-B");
        GatewayRefreshTokenHasher hasherB = new GatewayRefreshTokenHasher(propertiesB);

        String hashA = hasherA.hash("refresh-token");
        String hashB = hasherB.hash("refresh-token");

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void hash_throwsWhenTokenBlank() {
        GatewaySessionProperties properties = new GatewaySessionProperties();
        GatewayRefreshTokenHasher hasher = new GatewayRefreshTokenHasher(properties);

        assertThatThrownBy(() -> hasher.hash(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refreshToken");
    }
}
