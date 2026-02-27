package com.example.search.service.index;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexTemplateResolverTest {

    private final SearchIndexTemplateResolver resolver = new SearchIndexTemplateResolver(new DefaultResourceLoader());

    @Test
    void resolveItemsIndexTemplate_includesNoriAndDictionaries() {
        String template = resolver.resolveItemsIndexTemplate();

        assertThat(template).contains("\"decompound_mode\": \"mixed\"");
        assertThat(template).contains("\"ko_synonym_filter\"");
        assertThat(template).contains("\"ko_stop_filter\"");
        assertThat(template).contains("\"user_dictionary_rules\"");
        assertThat(template).contains("\"stock\"");
        assertThat(template).contains("\"effectivePrice\"");
        assertThat(template).contains("\"salesChannel\"");
        assertThat(template).contains("\"channelPriority\"");
        assertThat(template).contains("\"activeHotDealId\"");
        assertThat(template).contains("\"activeCampaignId\"");
        assertThat(template).contains("아이폰, iphone, i-phone");
        assertThat(template).contains("그리고");
        assertThat(template).contains("에어팟프로");
        assertThat(template).doesNotContain("__SYNONYMS__");
        assertThat(template).doesNotContain("__STOPWORDS__");
        assertThat(template).doesNotContain("__USERDICT__");
    }
}
