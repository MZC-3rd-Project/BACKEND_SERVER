package com.example.funding.client;

import com.example.security.gateway.GatewaySecurityModuleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class FundingOrderLookupClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toSnapshot_parsesStringSerializedOrderId() throws Exception {
        FundingOrderLookupClient client = new FundingOrderLookupClient(
                WebClient.builder(),
                new GatewaySecurityModuleProperties(),
                "http://localhost:8090"
        );

        JsonNode data = objectMapper.readTree("""
                {
                  "orderId": "12345",
                  "userId": 1001,
                  "status": "PAID",
                  "items": [
                    {
                      "channelType": "FUNDING",
                      "channelRefId": 4401,
                      "itemId": 930002,
                      "quantity": 3,
                      "lineAmount": 30000
                    }
                  ]
                }
                """);

        FundingOrderSnapshot snapshot = ReflectionTestUtils.invokeMethod(client, "toSnapshot", data);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.orderId()).isEqualTo(12345L);
        assertThat(snapshot.userId()).isEqualTo(1001L);
        assertThat(snapshot.status()).isEqualTo("PAID");
        assertThat(snapshot.lineItems()).hasSize(1);
        assertThat(snapshot.lineItems().get(0).channelType()).isEqualTo("FUNDING");
        assertThat(snapshot.lineItems().get(0).channelRefId()).isEqualTo(4401L);
        assertThat(snapshot.lineItems().get(0).itemId()).isEqualTo(930002L);
        assertThat(snapshot.lineItems().get(0).quantity()).isEqualTo(3);
        assertThat(snapshot.lineItems().get(0).lineAmount()).isEqualTo(30000L);
    }
}
