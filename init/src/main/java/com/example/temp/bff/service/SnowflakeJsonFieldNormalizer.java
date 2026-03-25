package com.example.gateway.bff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SnowflakeJsonFieldNormalizer {

    private SnowflakeJsonFieldNormalizer() {
    }

    static void normalizeSuccessData(JsonNode responseBody) {
        if (!(responseBody instanceof ObjectNode body)) {
            return;
        }

        JsonNode data = body.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            return;
        }

        normalizeNode(data, null);
    }

    private static void normalizeNode(JsonNode node, String parentFieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            normalizeObject((ObjectNode) node);
            return;
        }

        if (node.isArray()) {
            normalizeArray((ArrayNode) node, parentFieldName);
        }
    }

    private static void normalizeObject(ObjectNode objectNode) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        objectNode.fields().forEachRemaining(fields::add);

        for (Map.Entry<String, JsonNode> field : fields) {
            String fieldName = field.getKey();
            JsonNode value = field.getValue();

            if (isIdField(fieldName) && value.isIntegralNumber()) {
                objectNode.put(fieldName, value.asText());
                continue;
            }

            normalizeNode(value, fieldName);
        }
    }

    private static void normalizeArray(ArrayNode arrayNode, String parentFieldName) {
        for (int index = 0; index < arrayNode.size(); index++) {
            JsonNode value = arrayNode.get(index);
            if (isIdArrayField(parentFieldName) && value != null && value.isIntegralNumber()) {
                arrayNode.set(index, TextNode.valueOf(value.asText()));
                continue;
            }

            normalizeNode(value, parentFieldName);
        }
    }

    private static boolean isIdField(String fieldName) {
        return "id".equals(fieldName) || (fieldName != null && fieldName.endsWith("Id"));
    }

    private static boolean isIdArrayField(String fieldName) {
        return fieldName != null && fieldName.endsWith("Ids");
    }
}
