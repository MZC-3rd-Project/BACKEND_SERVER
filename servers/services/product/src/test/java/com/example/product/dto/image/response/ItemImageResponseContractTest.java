package com.example.product.dto.image.response;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemImageResponseContractTest {

    @Test
    void itemImageResponse_doesNotExposeImageUrlField() {
        List<String> fieldNames = Arrays.stream(ItemImageResponse.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("imageUrl");
    }
}
