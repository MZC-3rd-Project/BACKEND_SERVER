package com.example.notification.service.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRenderServiceTest {

    private final TemplateRenderService templateRenderService = new TemplateRenderService();

    @Test
    void render_replacesTemplateVariables() {
        String rendered = templateRenderService.render(
                "안녕하세요 {{userName}}, {{itemName}} 오픈!",
                Map.of("userName", "딩주", "itemName", "봄 콘서트")
        );

        assertThat(rendered).isEqualTo("안녕하세요 딩주, 봄 콘서트 오픈!");
    }

    @Test
    void render_keepsEmptyForMissingVariables() {
        String rendered = templateRenderService.render(
                "{{known}}/{{unknown}}",
                Map.of("known", "ok")
        );

        assertThat(rendered).isEqualTo("ok/");
    }
}
