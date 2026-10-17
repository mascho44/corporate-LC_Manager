package de.ostms.lc.document.service;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** Only explicit LC labels; never infer an LC reference from an invoice number. */
final class DocumentReferenceDetector {
private static final Pattern LABEL = Pattern.compile("(?im)(?<![\\p{L}\\p{N}-])(?:letter of credit|documentary credit|l\\.?/?c\\.?|akkreditiv)(?![\\p{L}\\p{N}])(?:[\\t ]*+(?:no\\.?|number|reference|ref\\.?|nummer|nr\\.?|referenz|#))?[\\t ]*+[:#-]?[\\t ]*+(?:\\r?\\n[\\t ]*+)?([A-Z0-9][A-Z0-9./_-]{3,}+)(?![A-Z0-9./_-])");
    /** "Credit No." / "Credit Ref." as printed on insurance certificates and similar documents; the suffix is required so ordinary uses of "credit" never match. */
    private static final Pattern CREDIT = Pattern.compile("(?im)(?<![\\p{L}\\p{N}-])credit[\\t ]*+(?:no\\.?|number|ref\\.?|reference|nr\\.?)[\\t ]*+[:#-]?[\\t ]*+(?:\\r?\\n[\\t ]*+)?([A-Z0-9][A-Z0-9./_-]{3,}+)(?![A-Z0-9./_-])");
    /** A value taken from the next line although the label stands in a row of several captions ("Invoice No.: Invoice Date: Credit No.:") belongs to the first caption, not to this one. */
    private static boolean fromCaptionRow(String text, java.util.regex.Matcher match) {
        int lineStart = text.lastIndexOf('\n', match.start()) + 1, lineEnd = text.indexOf('\n', match.start());
        if (lineEnd < 0 || match.start(1) <= lineEnd) return false;
        return text.substring(lineStart, lineEnd).chars().filter(c -> c == ':').count() >= 2;
    }
    static String detect(String text) {
        if (text == null) return null;
        var values = new LinkedHashSet<String>();
        String head = text.substring(0, Math.min(text.length(),100_000));
        for (var pattern : java.util.List.of(LABEL, CREDIT)) {
            var matcher = pattern.matcher(head);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (value.chars().anyMatch(Character::isDigit) && !fromCaptionRow(head, matcher)) values.add(value);
            }
        }
        return values.size()==1 ? values.iterator().next() : null;
    }
}
