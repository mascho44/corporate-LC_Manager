package de.ostms.lc.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * English texts for the German messages of the server (errors shown on screen). The catalog {@code server-messages-en.json} maps the exact German message
 * to its English text; messages with variable parts are matched by a template ("Der Host „{}“ ist nicht auflösbar." with {1} in the English text).
 * Unknown messages stay as they are.
 */
public final class ServerMessages {
    private record Template(Pattern german, String english) {}
    private static final Map<String, String> EXACT = new HashMap<>();
    private static final List<Template> TEMPLATES = new ArrayList<>();
    static {
        try (InputStream in = ServerMessages.class.getResourceAsStream("/server-messages-en.json")) {
            if (in != null) {
                JsonNode root = new ObjectMapper().readTree(in);
                root.get("exact").properties().forEach(e -> EXACT.put(e.getKey(), e.getValue().asText()));
                for (JsonNode node : root.get("patterns")) TEMPLATES.add(new Template(compile(node.get("de").asText()), node.get("en").asText()));
            }
        } catch (Exception unreadable) { /* without the catalog the German texts are shown */ }
    }
    private ServerMessages() {}

    private static Pattern compile(String template) {
        StringBuilder regex = new StringBuilder("^");
        String[] parts = template.split("\\{\\}", -1);
        for (int i = 0; i < parts.length; i++) { if (i > 0) regex.append("(.+?)"); regex.append(Pattern.quote(parts[i])); }
        return Pattern.compile(regex.append("$").toString(), Pattern.DOTALL);
    }

    /** The message in the requested language ("en" translates, anything else returns the German original). */
    public static String translate(String message, String language) {
        if (message == null || !"en".equalsIgnoreCase(language == null ? "" : language.trim())) return message;
        String exact = EXACT.get(message);
        if (exact != null) return exact;
        for (Template template : TEMPLATES) {
            Matcher matcher = template.german().matcher(message);
            if (matcher.matches()) {
                String english = template.english();
                for (int i = 1; i <= matcher.groupCount(); i++) english = english.replace("{" + i + "}", matcher.group(i));
                return english;
            }
        }
        return message;
    }
}
