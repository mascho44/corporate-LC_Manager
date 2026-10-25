package de.ostms.lc.rulepack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** LC fields the examiner marked as "not required by the credit": rules that only lack such a field are not applicable. */
public final class RuleWaivers {
    private static final ObjectMapper JSON = new ObjectMapper();
    private RuleWaivers() {}

    public static Set<Field> read(String json) {
        if (json == null || json.isBlank()) return EnumSet.noneOf(Field.class);
        try {
            var out = EnumSet.noneOf(Field.class);
            for (String name : JSON.readValue(json, new TypeReference<List<String>>() {})) {
                try { out.add(Field.valueOf(name)); } catch (IllegalArgumentException unknown) { /* removed field: ignore */ }
            }
            return out;
        } catch (Exception unreadable) {
            return EnumSet.noneOf(Field.class);
        }
    }

    /** Adds or removes one LC field; only LC-side fields can be waived. */
    public static String update(String json, Field field, boolean waived) {
        if (!field.name().startsWith("LC_")) throw new IllegalArgumentException("Nur LC-Angaben können als nicht verlangt markiert werden.");
        var set = read(json);
        if (waived) set.add(field); else set.remove(field);
        try {
            return set.isEmpty() ? null : JSON.writeValueAsString(set.stream().map(Field::name).sorted().toList());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
