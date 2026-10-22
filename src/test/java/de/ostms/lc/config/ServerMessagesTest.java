package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class ServerMessagesTest {
    @Test void englishComesFromTheCatalogAndGermanStaysTheDefault() {
        assertThat(ServerMessages.translate("Bitte einen Kunden auswählen.", "en")).isEqualTo("Please choose a customer.");
        assertThat(ServerMessages.translate("Bitte einen Kunden auswählen.", "de")).isEqualTo("Bitte einen Kunden auswählen.");
        assertThat(ServerMessages.translate("Bitte einen Kunden auswählen.", null)).isEqualTo("Bitte einen Kunden auswählen.");
        assertThat(ServerMessages.translate("Völlig unbekannte Meldung.", "en")).isEqualTo("Völlig unbekannte Meldung.");
    }

    @Test void messagesWithVariablePartsAreMatchedByTemplate() {
        assertThat(ServerMessages.translate("Der Host „example.org“ ist nicht auflösbar.", "en")).isEqualTo("The host “example.org” cannot be resolved.");
        assertThat(ServerMessages.translate("Feld :31E: enthält kein gültiges SWIFT-Datum (JJMMTT): „X“.", "en")).isEqualTo("Field :31E: does not contain a valid SWIFT date (YYMMDD): “X”.");
        assertThat(ServerMessages.translate("Es sind bereits 20 Zugänge aktiv. Bitte nicht mehr benötigte widerrufen.", "en")).isEqualTo("20 accesses are already active. Please revoke those no longer needed.");
    }

    /** New German error texts must get an English text in server-messages-en.json (single-literal messages of the usual exceptions). */
    @Test void everySimpleGermanExceptionMessageHasAnEnglishText() throws IOException {
        Pattern exception = Pattern.compile("new (?:IllegalArgumentException|IllegalStateException|NoSuchElementException|AccessDeniedException)\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\s*[,)]");
        Pattern german = Pattern.compile("[äöüÄÖÜß]|\\b(und|der|die|das|nicht|kein|keine|ist|wird|muss|bitte|für|von|mit|zu|im|dem|den|eine?)\\b");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = exception.matcher(Files.readString(file));
                while (m.find()) {
                    String text = m.group(1).replace("\\\"", "\"");
                    if (german.matcher(text).find() && ServerMessages.translate(text, "en").equals(text)) missing.add(file.getFileName() + ": " + text);
                }
            }
        }
        assertThat(missing).as("German server messages without English text").isEmpty();
    }

    @Test void jsonAnswersWithErrorOrMessageAreTranslatedForEnglishRequests() {
        var advice = new ServerMessageTranslation();
        var english = new org.springframework.http.server.ServletServerHttpRequest(englishRequest("en"));
        var german = new org.springframework.http.server.ServletServerHttpRequest(englishRequest(null));
        Object body = java.util.Map.of("error", "Benutzername oder Passwort ist falsch.");
        assertThat(((java.util.Map<?, ?>) advice.beforeBodyWrite(body, null, null, null, english, null)).get("error")).isEqualTo("User name or password is wrong.");
        assertThat(advice.beforeBodyWrite(body, null, null, null, german, null)).isSameAs(body);
        Object other = java.util.List.of("x");
        assertThat(advice.beforeBodyWrite(other, null, null, null, english, null)).isSameAs(other);
    }
    private static org.springframework.mock.web.MockHttpServletRequest englishRequest(String language) {
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        if (language != null) request.addHeader("X-LC-Language", language);
        return request;
    }
}
