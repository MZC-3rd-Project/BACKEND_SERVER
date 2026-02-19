package com.example.notification.service.template;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateRenderService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public String render(String template, Map<String, Object> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }

        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = safeVariables.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
