package de.ostms.lc.rulepack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            var filled = merge(facts, documents.suggest(doc));
            if (filled.isEmpty()) return 0;
            doc.setRuleFactsJson(RuleFacts.encode(facts, true));
            var auto = read(doc.getAutoRuleFacts()); auto.addAll(filled); doc.setAutoRuleFacts(write(auto));
            return filled.size();
        } catch (RuntimeException unusable) { return 0; } // recognition is a convenience; the document is stored anyway
    }

    /** For a file right after import (parties, ports, goods from the message). */
    public int apply(LetterOfCredit lc) {
        if (letters == null || lc == null) return 0;
        try {
            var facts = new EnumMap<Field, String>(Field.class); facts.putAll(RuleFacts.read(lc.getRuleFactsJson()));
            var filled = merge(facts, letters.suggest(lc));
            if (filled.isEmpty()) return 0;
            lc.setRuleFactsJson(RuleFacts.encode(facts, false));
            var auto = read(lc.getAutoRuleFacts()); auto.addAll(filled); lc.setAutoRuleFacts(write(auto));
            return filled.size();
        } catch (RuntimeException unusable) { return 0; }
    }
}
