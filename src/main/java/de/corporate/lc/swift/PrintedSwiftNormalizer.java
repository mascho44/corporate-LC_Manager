package de.corporate.lc.swift;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.*;

@Component
public class PrintedSwiftNormalizer {
    private static final Pattern COLON_FIELD = Pattern.compile("^.*?:(\\d{2}[A-Z]?):\\s*(.*)$");
    private static final Pattern TABLE_FIELD = Pattern.compile("^\\s*(\\d{2}[A-Z]?)\\s+(.+?)\\s*$");
    private static final Set<String> KNOWN_FIELDS = Set.of(
            "20", "21", "23", "27", "30", "31C", "31D", "32B", "39A", "39B", "39C",
            "40A", "40E", "41A", "41D", "42A", "42C", "42M", "42P", "43P", "43T",
            "44A", "44B", "44C", "44D", "44E", "44F", "45A", "46A", "47A", "48", "49",
            "50", "51A", "51D", "52A", "52D", "53A", "53D", "54A", "54D", "56A", "56D",
            "57A", "57D", "58A", "58D", "59", "71B", "71D", "72", "72Z", "77C", "77U", "78", "79"
    );

    public String normalize(String text) {
        if (text == null) return "";
        List<String> out = new ArrayList<>();
        String code = null;
        StringBuilder value = new StringBuilder();
        for (String line : text.replace('\f', '\n').split("\\R")) {
            Matcher colon = COLON_FIELD.matcher(line);
            Matcher table = TABLE_FIELD.matcher(line);
            if (colon.matches()) {
                if (code != null) add(out, code, value);
                code = colon.group(1);
                value = new StringBuilder(colon.group(2).trim());
            } else if (table.matches() && KNOWN_FIELDS.contains(table.group(1))) {
                if (code != null) add(out, code, value);
                code = table.group(1);
                value = new StringBuilder(); // The remainder is the printed field label, not its value.
            } else if (code != null && !line.isBlank() && !ignored(line)) {
                String continuation = line.strip();
                if (!continuation.isBlank()) {
                    if (!value.isEmpty()) value.append('\n');
                    value.append(continuation);
                }
            }
        }
        if (code != null) add(out, code, value);
        return String.join("\n", out);
    }

    private boolean ignored(String line) {
        return line.matches("(?i)^\\s*(Seite|Page|QUOTE:|UNQUOTE|Trailer|Checksum|Fieldtag\\s+Fieldname|Fielddetails|Message Details|Header).*" );
    }

    private void add(List<String> out, String code, StringBuilder value) {
        out.add(":" + code + ":" + value.toString().trim());
    }
}
