package com.example.media.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaOwnerTypeTest {

    @Test
    void fromNullable_whenStoreValueNormalizesToStore() {
        assertThat(MediaOwnerType.fromNullable("store")).isEqualTo(MediaOwnerType.STORE);
        assertThat(MediaOwnerType.fromNullable("Store")).isEqualTo(MediaOwnerType.STORE);
        assertThat(MediaOwnerType.fromNullable(" STORE ")).isEqualTo(MediaOwnerType.STORE);
    }

    @Test
    void fromNullable_whenUserProfileHyphenValueNormalizes() {
        assertThat(MediaOwnerType.fromNullable("user-profile")).isEqualTo(MediaOwnerType.USER_PROFILE);
    }

    @Test
    void fromNullable_whenBlankReturnsNull() {
        assertThat(MediaOwnerType.fromNullable(" ")).isNull();
        assertThat(MediaOwnerType.fromNullable(null)).isNull();
    }
}
