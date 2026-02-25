package com.example.gateway.bff.dto.catalog;

import com.example.gateway.bff.dto.BffItemType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record CatalogQueryParams(
        String query,
        String category,
        BffItemType itemType,
        CatalogSalesChannel channel,
        List<String> statuses,
        Long minPrice,
        Long maxPrice,
        String sort,
        String cursor,
        Integer size
) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "LATEST",
            "POPULAR",
            "PRICE_ASC",
            "PRICE_DESC"
    );
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "DRAFT",
            "FUNDING",
            "FUNDED",
            "FUND_FAILED",
            "ON_SALE",
            "HOT_DEAL",
            "HIDDEN",
            "SOLD_OUT",
            "CLOSED"
    );
    private static final Map<CatalogSalesChannel, Set<String>> CHANNEL_ALLOWED_STATUSES = Map.of(
            CatalogSalesChannel.HOT_DEAL, Set.of("HOT_DEAL"),
            CatalogSalesChannel.FUNDING, Set.of("FUNDING", "FUNDED", "FUND_FAILED"),
            CatalogSalesChannel.NORMAL, Set.of("ON_SALE")
    );

    public static CatalogQueryParams from(ServerHttpRequest request) {
        MultiValueMap<String, String> queryParams = request.getQueryParams();

        String query = trimToNull(queryParams.getFirst("q"));
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("q는 필수입니다");
        }

        String category = trimToNull(queryParams.getFirst("category"));
        BffItemType itemType = parseItemType(queryParams.getFirst("itemType"));
        CatalogSalesChannel channel = CatalogSalesChannel.fromNullable(queryParams.getFirst("channel"));
        List<String> statuses = parseStatuses(queryParams.get("status"));
        Long minPrice = parseNonNegativeLong(queryParams.getFirst("minPrice"), "minPrice");
        Long maxPrice = parseNonNegativeLong(queryParams.getFirst("maxPrice"), "maxPrice");
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice는 maxPrice보다 클 수 없습니다");
        }

        String sort = parseSort(queryParams.getFirst("sort"));
        String cursor = trimToNull(queryParams.getFirst("cursor"));
        Integer size = parseSize(queryParams.getFirst("size"));

        validateStatuses(statuses);
        validateChannelAndStatuses(channel, statuses);

        return new CatalogQueryParams(
                query,
                category,
                itemType,
                channel,
                statuses,
                minPrice,
                maxPrice,
                sort,
                cursor,
                size
        );
    }

    public List<String> resolvedStatuses() {
        if (!statuses.isEmpty()) {
            return statuses;
        }
        if (channel == CatalogSalesChannel.ALL) {
            return List.of();
        }
        return List.copyOf(CHANNEL_ALLOWED_STATUSES.getOrDefault(channel, Set.of()));
    }

    public MultiValueMap<String, String> toSearchQueryParams() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        addIfPresent(params, "category", category);
        if (itemType != null) {
            params.add("domainType", itemType.name());
        }
        for (String status : resolvedStatuses()) {
            params.add("status", status);
        }
        if (minPrice != null) {
            params.add("minPrice", String.valueOf(minPrice));
        }
        if (maxPrice != null) {
            params.add("maxPrice", String.valueOf(maxPrice));
        }
        addIfPresent(params, "sort", sort);
        addIfPresent(params, "cursor", cursor);
        params.add("size", String.valueOf(size));
        return params;
    }

    public MultiValueMap<String, String> toDegradeSearchQueryParams() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", query);
        addIfPresent(params, "category", category);
        if (itemType != null) {
            params.add("domainType", itemType.name());
        }
        if (minPrice != null) {
            params.add("minPrice", String.valueOf(minPrice));
        }
        if (maxPrice != null) {
            params.add("maxPrice", String.valueOf(maxPrice));
        }
        addIfPresent(params, "sort", sort);
        addIfPresent(params, "cursor", cursor);
        params.add("size", String.valueOf(size));
        return params;
    }

    private static void addIfPresent(MultiValueMap<String, String> params, String key, String value) {
        if (StringUtils.hasText(value)) {
            params.add(key, value);
        }
    }

    private static BffItemType parseItemType(String raw) {
        String normalized = trimToNull(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return BffItemType.fromNullable(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("itemType은 PRODUCT, GOODS, PERFORMANCE 중 하나여야 합니다");
        }
    }

    private static String parseSort(String raw) {
        String normalized = trimToNull(raw);
        if (!StringUtils.hasText(normalized)) {
            return "LATEST";
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_SORTS.contains(upper)) {
            throw new IllegalArgumentException("sort는 LATEST, POPULAR, PRICE_ASC, PRICE_DESC 중 하나여야 합니다");
        }
        return upper;
    }

    private static Integer parseSize(String raw) {
        String normalized = trimToNull(raw);
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SIZE;
        }
        try {
            int parsed = Integer.parseInt(normalized);
            if (parsed < 1 || parsed > MAX_SIZE) {
                throw new IllegalArgumentException("size는 1 이상 100 이하여야 합니다");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("size는 정수여야 합니다");
        }
    }

    private static Long parseNonNegativeLong(String raw, String fieldName) {
        String normalized = trimToNull(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(normalized);
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + "는 0 이상이어야 합니다");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + "는 정수여야 합니다");
        }
    }

    private static List<String> parseStatuses(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (!StringUtils.hasText(rawValue)) {
                continue;
            }
            String[] tokens = rawValue.split(",");
            for (String token : tokens) {
                String status = trimToNull(token);
                if (!StringUtils.hasText(status)) {
                    continue;
                }
                normalized.add(status.toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static void validateStatuses(List<String> statuses) {
        for (String status : statuses) {
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new IllegalArgumentException("지원하지 않는 status 값입니다: " + status);
            }
        }
    }

    private static void validateChannelAndStatuses(CatalogSalesChannel channel, List<String> statuses) {
        if (channel == CatalogSalesChannel.ALL || statuses.isEmpty()) {
            return;
        }
        Set<String> allowedByChannel = CHANNEL_ALLOWED_STATUSES.getOrDefault(channel, Set.of());

        for (String status : statuses) {
            if (!allowedByChannel.contains(status)) {
                throw new IllegalArgumentException("channel과 status 조합이 유효하지 않습니다");
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
