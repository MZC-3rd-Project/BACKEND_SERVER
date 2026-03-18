package com.example.gateway.bff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeJsonFieldNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizeSuccessData_convertsSnowflakeLikeFieldsToStringsOnly() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", true);
        body.set("data", objectMapper.readTree("""
                {
                  "id": 1001,
                  "price": 39000,
                  "campaignId": 2002,
                  "checkout": {
                    "itemId": 3003
                  },
                  "rewardOptions": [
                    {
                      "id": 91,
                      "itemOptionId": 501,
                      "price": 12000
                    }
                  ],
                  "galleryMediaIds": [11, 12],
                  "supporterCount": 7
                }
                """));

        SnowflakeJsonFieldNormalizer.normalizeSuccessData(body);

        assertThat(body.path("data").path("id").isTextual()).isTrue();
        assertThat(body.path("data").path("id").asText()).isEqualTo("1001");
        assertThat(body.path("data").path("campaignId").isTextual()).isTrue();
        assertThat(body.path("data").path("campaignId").asText()).isEqualTo("2002");
        assertThat(body.path("data").path("checkout").path("itemId").isTextual()).isTrue();
        assertThat(body.path("data").path("checkout").path("itemId").asText()).isEqualTo("3003");
        assertThat(body.path("data").path("rewardOptions").get(0).path("id").isTextual()).isTrue();
        assertThat(body.path("data").path("rewardOptions").get(0).path("itemOptionId").isTextual()).isTrue();
        assertThat(body.path("data").path("galleryMediaIds").get(0).isTextual()).isTrue();
        assertThat(body.path("data").path("galleryMediaIds").get(0).asText()).isEqualTo("11");
        assertThat(body.path("data").path("price").isNumber()).isTrue();
        assertThat(body.path("data").path("supporterCount").isNumber()).isTrue();
    }
}
