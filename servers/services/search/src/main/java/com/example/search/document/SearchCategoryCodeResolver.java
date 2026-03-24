package com.example.search.document;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SearchCategoryCodeResolver {

    private static final Map<String, List<String>> CATEGORY_CODE_ALIASES = createAliases();

    private SearchCategoryCodeResolver() {
    }

    static List<String> resolve(String category, List<String> categoryPath, String fundingCategory) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        collect(resolved, fundingCategory);
        collect(resolved, category);
        if (categoryPath != null) {
            categoryPath.forEach(path -> collect(resolved, path));
        }
        if (resolved.stream().anyMatch(SearchCategoryCodeResolver::isCollectibleChildCode)) {
            resolved.add("COLLECTIBLE");
        }
        return List.copyOf(resolved);
    }

    static String normalizeCode(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static void collect(Set<String> resolved, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return;
        }

        String normalizedCode = normalizeCode(candidate);
        if (looksLikeCode(normalizedCode)) {
            resolved.add(normalizedCode);
        }

        String normalizedText = normalizeText(candidate);
        for (Map.Entry<String, List<String>> entry : CATEGORY_CODE_ALIASES.entrySet()) {
            if (entry.getValue().stream()
                    .map(SearchCategoryCodeResolver::normalizeText)
                    .anyMatch(normalizedText::contains)) {
                resolved.add(entry.getKey());
            }
        }
    }

    private static boolean looksLikeCode(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        for (char ch : candidate.toCharArray()) {
            if (!(Character.isUpperCase(ch) || Character.isDigit(ch) || ch == '_')) {
                return false;
            }
        }
        return candidate.chars().anyMatch(Character::isLetter);
    }

    private static boolean isCollectibleChildCode(String code) {
        return switch (code) {
            case "DESK", "APPAREL", "LIVING", "ACCESSORY" -> true;
            default -> false;
        };
    }

    private static String normalizeText(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        return rawValue.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    private static Map<String, List<String>> createAliases() {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("COLLECTIBLE", List.of(
                "collectible",
                "굿즈",
                "구장굿즈",
                "응원굿즈",
                "팬굿즈",
                "merch",
                "merchandise",
                "officialmd",
                "md"
        ));
        aliases.put("DESK", List.of(
                "desk",
                "문구",
                "데스크",
                "캘린더",
                "달력",
                "stationery",
                "노트",
                "스티커"
        ));
        aliases.put("APPAREL", List.of(
                "apparel",
                "의류",
                "패션",
                "웨어",
                "clothing",
                "후드",
                "티셔츠",
                "저지"
        ));
        aliases.put("LIVING", List.of(
                "living",
                "리빙",
                "생활",
                "home",
                "머그",
                "텀블러",
                "주방"
        ));
        aliases.put("ACCESSORY", List.of(
                "accessory",
                "액세서리",
                "악세서리",
                "키링",
                "폰케이스"
        ));
        return Map.copyOf(aliases);
    }
}
