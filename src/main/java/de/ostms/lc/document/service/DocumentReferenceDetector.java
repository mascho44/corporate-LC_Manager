package de.ostms.lc.document.service;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** Only explicit LC labels; never infer an LC reference from an invoice number. */
final class DocumentReferenceDetector {
    private static final Pattern LABEL = Pattern.compile("(?im)(?<![\\p{L}\\p{N}])(?:letter of credit|documentary credit|l\\.?/?c\\.?|akkreditiv)(?:[\\t ]*(?:no\\.?|number|reference|ref\\.?|nummer|nr\\.?|referenz|#))?[\\t ]*[:#-]?[\\t ]*(?:\\r?\\n[\\t ]*)?([A-Z0-9][A-Z0-9./_-]{3,})(?![A-Z0-9./_-])");
    static String detect(String text) {
        if (text == null) return null;
        var values = new LinkedHashSet<String>();
        var matcher = LABEL.matcher(text.substring(0, Math.min(text.length(),100_000)));
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.chars().anyMatch(Character::isDigit)) values.add(value);
        }
        return values.size()==1 ? values.iterator().next() : null;
    }
}
