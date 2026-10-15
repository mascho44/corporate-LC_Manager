package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * How often the automatic document type was right, per confirmed type: the first automatic suggestion of a document compared with the type a person confirmed.
 * Counts documents in LC files and confirmed, not yet attached inbox documents (attached ones are the file documents and would count twice).
 */
@Service
public class DocumentTypeAccuracy {
    static final int LIMIT = 5000;
    public record Row(String type, String label, int documents, int correct, int correctSafe, int safe, int wrong, int none) {
        @com.fasterxml.jackson.annotation.JsonProperty("rate") public Double rate() { return documents == 0 ? null : (double) correct / documents; }
    }
    public record Mistake(String suggested, String confirmed, int count) {}
    public record Report(int documents, int correct, List<Row> types, List<Mistake> mistakes) {
        @com.fasterxml.jackson.annotation.JsonProperty("rate") public Double rate() { return documents == 0 ? null : (double) correct / documents; }
    }
    private final JdbcTemplate jdbc;
    public DocumentTypeAccuracy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Report report() {
        UUID tenant = TenantContext.currentId();
        var histories = new ArrayList<String>();
        histories.addAll(jdbc.queryForList("select classification_history_json from lc_document where tenant_id=? and classification_history_json is not null order by uploaded_at desc limit " + LIMIT, String.class, tenant));
        histories.addAll(jdbc.queryForList("select classification_history_json from document_inbox_item where tenant_id=? and status='OPEN' and classification_history_json is not null order by received_at desc limit " + LIMIT, String.class, tenant));
        return evaluate(histories);
    }

    /** Pure evaluation of classification histories; histories without a confirmed type are not measured. */
    static Report evaluate(List<String> histories) {
        var rows = new TreeMap<DocumentType, int[]>(); // documents, correct, correctSafe, safe, wrong, none
        var mistakes = new HashMap<String, Integer>();
        int documents = 0, correct = 0;
        for (String history : histories) {
            DocumentType confirmed; DocumentClassifier.Classification suggestion;
            try { confirmed = ClassificationHistory.selectedType(history); suggestion = ClassificationHistory.suggestion(history); }
            catch (RuntimeException unreadable) { continue; }
            if (confirmed == null || confirmed == DocumentType.OTHER) continue;
            var r = rows.computeIfAbsent(confirmed, k -> new int[6]); r[0]++; documents++;
            DocumentType suggested = suggestion == null ? null : suggestion.suggestedType();
            boolean safe = suggestion != null && "SUGGESTED".equals(suggestion.status());
            if (safe) r[3]++;
            if (suggested == null) r[5]++;
            else if (suggested == confirmed) { r[1]++; correct++; if (safe) r[2]++; }
            else { r[4]++; mistakes.merge(suggested.getDisplayName() + "→" + confirmed.getDisplayName(), 1, Integer::sum); }
        }
        var types = rows.entrySet().stream().map(e -> { var r = e.getValue(); return new Row(e.getKey().name(), e.getKey().getDisplayName(), r[0], r[1], r[2], r[3], r[4], r[5]); })
            .sorted(Comparator.comparingInt(Row::documents).reversed()).toList();
        var top = mistakes.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
            .map(e -> { String[] p = e.getKey().split("→", 2); return new Mistake(p[0], p[1], e.getValue()); }).toList();
        return new Report(documents, correct, types, top);
    }
}
