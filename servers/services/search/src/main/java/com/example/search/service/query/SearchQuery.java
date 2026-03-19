package com.example.search.service.query;

import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record SearchQuery(
        String query,
        String category,
        String domainType,
        List<String> statuses,
        Long minPrice,
        Long maxPrice,
        SearchSortType sortType,
        String cursor,
        int size
) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_DOMAIN_TYPES = Set.of("PRODUCT", "GOODS", "PERFORMANCE");
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
    private static final List<String> DEFAULT_PUBLIC_STATUSES = List.of(
            "FUNDING",
            "FUNDED",
            "ON_SALE",
            "HOT_DEAL"
    );

    public SearchQuery {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
    }

    public static SearchQuery of(
            String query,
            String category,
            String domainType,
            List<String> statuses,
            Long minPrice,
            Long maxPrice,
            String sort,
            String cursor,
            Integer size
    ) {
        Long safeMinPrice = validatePrice(minPrice, "minPrice");
        Long safeMaxPrice = validatePrice(maxPrice, "maxPrice");
        if (safeMinPrice != null && safeMaxPrice != null && safeMinPrice > safeMaxPrice) {
            throw new IllegalArgumentException("minPrice는 maxPrice보다 클 수 없습니다");
        }

        return new SearchQuery(
                trimToNull(query),
                trimToNull(category),
                normalizeDomainType(domainType),
                normalizeStatuses(statuses),
                safeMinPrice,
                safeMaxPrice,
                SearchSortType.fromNullable(sort),
                trimToNull(cursor),
                normalizeSize(size)
        );
    }

    public boolean hasKeyword() {
        return StringUtils.hasText(query);
    }

    public int offset() {
        return SearchCursorCodec.decodeOffset(cursor);
    }

    public List<String> resolvedStatuses() {
        return statuses.isEmpty() ? DEFAULT_PUBLIC_STATUSES : statuses;
    }

    private static String normalizeDomainType(String rawDomainType) {
        String normalized = trimToNull(rawDomainType);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!ALLOWED_DOMAIN_TYPES.contains(upper)) {
            throw new IllegalArgumentException("domainType은 PRODUCT, GOODS, PERFORMANCE 중 하나여야 합니다");
        }
        return upper;
    }

    private static List<String> normalizeStatuses(List<String> rawStatuses) {
        if (rawStatuses == null || rawStatuses.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String rawStatus : rawStatuses) {
            if (!StringUtils.hasText(rawStatus)) {
                continue;
            }

            for (String token : rawStatus.split(",")) {
                String candidate = trimToNull(token);
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }

                String upper = candidate.toUpperCase(Locale.ROOT);
                if (!ALLOWED_STATUSES.contains(upper)) {
                    throw new IllegalArgumentException("지원하지 않는 status 값이 포함되어 있습니다: " + candidate);
                }
                normalized.add(upper);
            }
        }
        return normalized.stream().filter(Objects::nonNull).toList();
    }

    private static Long validatePrice(Long price, String fieldName) {
        if (price == null) {
            return null;
        }
        if (price < 0L) {
            throw new IllegalArgumentException(fieldName + "는 0 이상이어야 합니다");
        }
        return price;
    }

    private static int normalizeSize(Integer rawSize) {
        int normalized = rawSize == null ? DEFAULT_SIZE : rawSize;
        if (normalized < 1 || normalized > MAX_SIZE) {
            throw new IllegalArgumentException("size는 1 이상 100 이하여야 합니다");
        }
        return normalized;
    }

    private static String trimToNull(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
