package de.ostms.lc.rulepack;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import de.ostms.lc.document.domain.LcDocument;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static de.ostms.lc.rulepack.PackDefinition.Field;

/**
 * Fills empty fields of the check data from the recognised text when a document reaches a file (and the file's own fields at import). Values a person entered
 * are never changed; the automatically filled fields are remembered until a person saves the check data (which confirms them).
 */
@Component
public class AutoRuleFacts {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired(required = false) private DocumentFactSuggester documents;
    @Autowired(required = false) private LcFactSuggester letters;
    @Autowired(required = false) private de.ostms.lc.document.repository.LcDocumentRepository documentRepository;

    /** Merges suggestions into empty fields; returns the names of the fields filled. */
    public static List<String> merge(Map<Field, String> facts, List<DocumentFactSuggester.Suggestion> suggestions) {
        var filled = new ArrayList<String>();
        for (var s : suggestions) {
            if (s.value() == null || s.value().isBlank()) continue;
            String current = facts.get(s.field());
            if (current == null || current.isBlank()) { facts.put(s.field(), s.value()); filled.add(s.field().name()); }
        }
        return filled;
    }
    /** Only suggestions read from a labelled place; guesses (marked "bitte prüfen", e.g. issuer from the letterhead) stay proposals for a person. */
    static List<DocumentFactSuggester.Suggestion> safe(List<DocumentFactSuggester.Suggestion> suggestions) {
        return suggestions.stream().filter(s -> s.source() == null || !s.source().toLowerCase(java.util.Locale.ROOT).matches("(?s).*(bitte pr(ü|ue)fen|prüfen\\b|unsicher|vermutlich).*")).toList();
    }
    public static Set<String> read(String json) {
        if (json == null || json.isBlank()) return new LinkedHashSet<>();
        try { return new LinkedHashSet<>(JSON.readValue(json, new TypeReference<List<String>>() {})); } catch (Exception unreadable) { return new LinkedHashSet<>(); }
    }
    public static String write(Set<String> fields) {
        try { return fields.isEmpty() ? null : JSON.writeValueAsString(List.copyOf(fields)); } catch (Exception e) { return null; }
    }

    /** For a document that is about to be saved in a file. Returns the number of fields filled. */
    public int apply(LcDocument doc) {
        if (documents == null || doc == null) return 0;
        try {
            var facts = new EnumMap<Field, String>(Field.class); facts.putAll(RuleFacts.read(doc.getRuleFactsJson()));
            var filled = merge(facts, safe(documents.suggest(doc)));
            if (filled.isEmpty()) return 0;
            doc.setRuleFactsJson(RuleFacts.encode(facts, true));
            var auto = read(doc.getAutoRuleFacts()); auto.addAll(filled); doc.setAutoRuleFacts(write(auto));
            return filled.size();
        } catch (RuntimeException unusable) { return 0; } // recognition is a convenience; the document is stored anyway
    }

    /** For a file right after import (parties, ports, goods from the message). */
    public int apply(LetterOfCredit lc) { return apply(lc, List.of()); }

    /** The file's own facts including what the documents already in the file tell (amounts of the invoice); the caller saves the file when the result is above zero. */
    public int applyWithDocuments(LetterOfCredit lc) {
        if (lc == null) return 0;
        List<de.ostms.lc.document.domain.LcDocument> docs = List.of();
        try { if (documentRepository != null && lc.getId() != null) docs = documentRepository.findByLetterOfCreditIdOrderByUploadedAtDesc(lc.getId()); } catch (RuntimeException unavailable) { /* amounts are only a convenience */ }
        return apply(lc, docs);
    }

    public int apply(LetterOfCredit lc, List<de.ostms.lc.document.domain.LcDocument> docs) {
        if (letters == null || lc == null) return 0;
        try {
            var facts = new EnumMap<Field, String>(Field.class); facts.putAll(RuleFacts.read(lc.getRuleFactsJson()));
            var filled = merge(facts, safe(letters.suggest(lc, docs)));
            if (filled.isEmpty()) return 0;
            lc.setRuleFactsJson(RuleFacts.encode(facts, false));
            var auto = read(lc.getAutoRuleFacts()); auto.addAll(filled); lc.setAutoRuleFacts(write(auto));
            return filled.size();
        } catch (RuntimeException unusable) { return 0; }
    }
}
