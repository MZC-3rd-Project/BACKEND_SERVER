package com.example.product.service.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMediaUrlNormalizerTest {

    private final ProductMediaUrlNormalizer normalizer =
        new ProductMediaUrlNormalizer("https://d179i4pv5hzdkg.cloudfront.net");

    @Test
    void normalize_rewritesRawS3UrlToCloudFront() {
        String normalized = normalizer.normalize(
            "https://team2-donmoa-media-raw.s3.ap-northeast-2.amazonaws.com/team2-donmoa-media/raw/2026/03/18/detail.png"
        );

        assertThat(normalized)
            .isEqualTo("https://d179i4pv5hzdkg.cloudfront.net/team2-donmoa-media/raw/2026/03/18/detail.png");
    }

    @Test
    void normalize_keepsNonS3UrlUntouched() {
        assertThat(normalizer.normalize("https://example.com/detail.png"))
            .isEqualTo("https://example.com/detail.png");
    }
}
