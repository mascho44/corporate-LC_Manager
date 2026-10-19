package de.ostms.lc.swift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Reads a documentary credit that is printed with field names but without SWIFT tag numbers (bank portal print, trade finance system,
 * advice letters in German or English: "Documentary Credit Number: LC-1", "Akkreditivnummer  LC-1", "Betrag: EUR 125.000,00") and
 * writes it back as ":tag:value" lines. Dates become YYMMDD and amounts "EUR125000,00" as in the MT700.
 * A label opens a field only at the start of a line and with a separator (colon or two spaces) or as the only text of the line.
 */
public final class LabelledCreditNormalizer {
    private record Label(String tag, String... names) {}
    private static final List<Label> LABELS = List.of(
        new Label("20", "documentary credit number", "documentary credit no", "letter of credit number", "letter of credit no", "l/c number", "l/c no", "lc number", "lc no", "credit number", "credit no", "akkreditivnummer", "akkreditiv-nummer", "akkreditiv-nr", "akkreditiv nr", "akkreditivreferenz", "referenznummer akkreditiv", "ihre referenz"),
        new Label("40A", "form of documentary credit", "form of credit", "art des akkreditivs", "akkreditivart"),
        new Label("40E", "applicable rules", "rules", "anwendbare regeln", "regelwerk"),
        new Label("31C", "date of issue", "issue date", "issuing date", "ausstellungsdatum", "ausstellungsdatum des akkreditivs", "eroeffnungsdatum", "eröffnungsdatum", "datum der eröffnung", "datum der eroeffnung", "ausgestellt am"),
        new Label("31D", "date and place of expiry", "expiry date and place", "expiry date", "date of expiry", "expiry", "ablaufdatum und ort", "ablaufdatum", "verfallsdatum und ort", "verfallsdatum", "verfalldatum und ort", "verfalldatum", "verfall und ort", "verfall", "gültig bis", "gueltig bis", "laufzeit bis"),
        new Label("32B", "currency code, amount", "currency code amount", "currency and amount", "credit amount", "amount of credit", "amount", "währung und betrag", "waehrung und betrag", "akkreditivbetrag", "betrag"),
        new Label("39A", "percentage credit amount tolerance", "tolerance", "toleranz"),
        new Label("50", "applicant", "applicant name", "auftraggeber", "akkreditivsteller", "antragsteller", "importeur"),
        new Label("59", "beneficiary", "begünstigter", "beguenstigter", "exporteur"),
        new Label("52D", "issuing bank", "opening bank", "issuing bank name", "eröffnende bank", "eroeffnende bank", "akkreditivbank", "eröffnungsbank", "eroeffnungsbank"),
        new Label("57D", "advising bank", "advise through bank", "avisierende bank", "avisbank", "zweitbank"),
        new Label("41D", "available with", "available with / by", "benutzbar bei", "zahlbar bei"),
        new Label("42C", "drafts at", "tenor", "payment terms", "zahlungsbedingung", "zahlungsbedingungen", "sichtzahlung"),
        new Label("43P", "partial shipments", "partial shipment", "teillieferungen", "teilverladungen", "teilverladung"),
        new Label("43T", "transhipment", "transshipment", "umladung", "umladungen"),
        new Label("44A", "place of receipt", "place of taking in charge", "übernahmeort", "uebernahmeort"),
        new Label("44E", "port of loading", "airport of departure", "port of loading/airport of departure", "verladehafen", "verschiffungshafen", "abgangshafen"),
        new Label("44F", "port of discharge", "airport of destination", "port of discharge/airport of destination", "entladehafen", "bestimmungshafen", "löschhafen", "loeschhafen"),
        new Label("44B", "place of final destination", "place of delivery", "endbestimmungsort", "bestimmungsort"),
        new Label("44C", "latest date of shipment", "latest shipment date", "latest shipment", "spätester verladetag", "spaetester verladetag", "spätester versandtermin", "spaetester versandtermin", "letzter verladetag", "verladefrist"),
        new Label("45A", "description of goods and/or services", "description of goods", "goods description", "goods", "warenbeschreibung", "beschreibung der waren", "warenbezeichnung", "ware"),
        new Label("46A", "documents required", "required documents", "geforderte dokumente", "dokumente", "erforderliche dokumente"),
        new Label("47A", "additional conditions", "other conditions", "zusätzliche bedingungen", "zusaetzliche bedingungen", "sonstige bedingungen", "besondere bedingungen"),
        new Label("48", "period for presentation in days", "period for presentation", "presentation period", "vorlagefrist", "vorlagefrist in tagen", "einreichungsfrist"),
        new Label("49", "confirmation instructions", "confirmation", "bestätigung", "bestaetigung", "bestätigungsanweisung")
    );
    private static final Map<String, String> BY_NAME = new HashMap<>();
    private static final Pattern LABEL_START;
    static {
        var names = new ArrayList<String>();
        for (Label label : LABELS) for (String name : label.names()) { BY_NAME.put(name, label.tag()); names.add(name); }
        names.sort(Comparator.comparingInt(String::length).reversed());
        var alternatives = new StringBuilder();
        for (String name : names) { if (alternatives.length() > 0) alternatives.append('|'); alternatives.append(Pattern.quote(name)); }
        LABEL_START = Pattern.compile("(?iu)^\\s*(" + alternatives + ")(?:\\s*[:\\t|]\\s*|\\s{2,}|\\s*$)(.*)$");
    }
    private static final Map<String, Month> MONTHS = new HashMap<>();
    private record Month(int number) {}
    static {
        String[][] names = {
            {"january", "januar", "jan"}, {"february", "februar", "feb"}, {"march", "märz", "maerz", "mar", "mär"}, {"april", "apr"}, {"may", "mai"}, {"june", "juni", "jun"},
            {"july", "juli", "jul"}, {"august", "aug"}, {"september", "sep", "sept"}, {"october", "oktober", "oct", "okt"}, {"november", "nov"}, {"december", "dezember", "dec", "dez"}};
        for (int i = 0; i < names.length; i++) for (String n : names[i]) MONTHS.put(n, new Month(i + 1));
    }

    private LabelledCreditNormalizer() {}

    /** ":tag:value" lines, or an empty string unless the text reads like a credit (number, amount and at least two more fields). */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";
        var fields = new LinkedHashMap<String, StringBuilder>();
        var seen = new HashSet<String>();
        String tag = null;
        for (String line : text.replace('\f', '\n').split("\\R")) {
            var match = LABEL_START.matcher(line);
            String opened = match.matches() ? BY_NAME.get(match.group(1).toLowerCase(Locale.ROOT)) : null;
            if (opened != null) {
                // A label that comes again keeps the first reading; its lines are skipped until the next label.
                tag = seen.add(opened) ? opened : null;
                if (tag != null) {
                    var value = fields.computeIfAbsent(tag, t -> new StringBuilder());
                    String rest = match.group(2).strip();
                    if (!rest.isEmpty()) value.append(rest);
                }
                continue;
            }
            if (tag != null && !line.isBlank() && !pageNoise(line)) {
                var value = fields.get(tag);
                if (value.length() > 0) value.append('\n');
                value.append(line.strip());
            }
        }
        var out = new ArrayList<String>();
        for (var entry : fields.entrySet()) {
            String value = convert(entry.getKey(), entry.getValue().toString().strip());
            if (value != null && !value.isBlank()) out.add(":" + (entry.getKey()) + ":" + value);
        }
        var present = new HashSet<String>();
        for (String line : out) present.add(line.substring(1, line.indexOf(':', 1)));
        boolean credit = present.contains("20") && present.contains("32B") && present.size() >= 4;
        return credit ? String.join("\n", out) : "";
    }

    private static boolean pageNoise(String line) {
        return line.matches("(?i)^\\s*(seite|page)\\s+\\d+(\\s*(von|of|/)\\s*\\d+)?\\s*$") || line.matches("^\\s*-{2,}\\s*\\d*\\s*-{0,}\\s*$");
    }

    private static String convert(String tag, String value) {
        if (value.isEmpty()) return null;
        return switch (tag) {
            case "20" -> firstLine(value);
            case "31C", "44C" -> yymmdd(value);
            case "31D" -> expiry(value);
            case "32B" -> amount(value);
            case "40A" -> revocability(value);
            case "40E" -> value.toUpperCase(Locale.ROOT).contains("UCP") ? "UCP LATEST VERSION" : firstLine(value).toUpperCase(Locale.ROOT);
            case "43P", "43T" -> allowed(value);
            case "48" -> { var m = Pattern.compile("\\d{1,3}").matcher(value); yield m.find() ? m.group() : null; }
            default -> value;
        };
    }

    private static String revocability(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("irrevoc") || v.contains("unwiderruf")) return "IRREVOCABLE";
        if (v.contains("revoc") || v.contains("widerruf")) return "REVOCABLE";
        return firstLine(value).toUpperCase(Locale.ROOT);
    }

    private static String firstLine(String value) { return value.split("\\R")[0].strip(); }

    private static String allowed(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("not allowed") || v.contains("prohibited") || v.contains("not permitted") || v.contains("nicht zulässig") || v.contains("nicht zulaessig") || v.contains("nicht erlaubt") || v.contains("verboten") || v.contains("untersagt") || v.equals("no") || v.equals("nein")) return "NOT ALLOWED";
        if (v.contains("allowed") || v.contains("permitted") || v.contains("zulässig") || v.contains("zulaessig") || v.contains("erlaubt") || v.equals("yes") || v.equals("ja")) return "ALLOWED";
        return value;
    }

    // ---- dates and amounts ------------------------------------------------------------------------------------------------------------------

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final Pattern DAY_MONTH_YEAR_NUMERIC = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern DAY_MONTH_NAME = Pattern.compile("(?iu)(?<!\\d)(\\d{1,2})\\.?\\s*(?:of\\s+)?(\\p{L}{3,9})\\.?,?\\s*(\\d{4}|\\d{2})(?!\\d)");
    private static final Pattern MONTH_NAME_DAY = Pattern.compile("(?iu)(\\p{L}{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})(?!\\d)");
    private static final Pattern SWIFT_DATE = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    /** The first date in the text as YYMMDD, or null. Numeric dates are read day first (as printed in Europe). */
    static String yymmdd(String text) {
        var range = findDate(text);
        return range == null ? null : range.date().format(YYMMDD);
    }

    private record Found(LocalDate date, int start, int end) {}

    private static Found findDate(String text) {
        Found best = null;
        var iso = ISO.matcher(text);
        if (iso.find()) best = earlier(best, make(iso.group(1), iso.group(2), iso.group(3), iso.start(), iso.end()));
        var numeric = DAY_MONTH_YEAR_NUMERIC.matcher(text);
        if (numeric.find()) best = earlier(best, make(year(numeric.group(3)), numeric.group(2), numeric.group(1), numeric.start(), numeric.end()));
        var named = DAY_MONTH_NAME.matcher(text);
        while (named.find()) {
            var month = MONTHS.get(named.group(2).toLowerCase(Locale.ROOT));
            if (month != null) { best = earlier(best, make(year(named.group(3)), String.valueOf(month.number()), named.group(1), named.start(), named.end())); break; }
        }
        var reversed = MONTH_NAME_DAY.matcher(text);
        while (reversed.find()) {
            var month = MONTHS.get(reversed.group(1).toLowerCase(Locale.ROOT));
            if (month != null) { best = earlier(best, make(reversed.group(3), String.valueOf(month.number()), reversed.group(2), reversed.start(), reversed.end())); break; }
        }
        var swift = SWIFT_DATE.matcher(text);
        if (best == null && swift.find()) best = make(year(swift.group(1).substring(0, 2)), swift.group(1).substring(2, 4), swift.group(1).substring(4, 6), swift.start(), swift.end());
        return best;
    }

    private static Found earlier(Found current, Found candidate) {
        if (candidate == null) return current;
        return current == null || candidate.start() < current.start() ? candidate : current;
    }

    private static String year(String y) { return y.length() == 2 ? "20" + y : y; }

    private static Found make(String year, String month, String day, int start, int end) {
        try { return new Found(LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)), start, end); }
        catch (RuntimeException invalid) { return null; }
    }

    /** ":31D:" is the date followed by the place of expiry: "30.11.2026, Hamburg" and "Hamburg, 30.11.2026" both become "261130HAMBURG". */
    private static String expiry(String value) {
        var found = findDate(value);
        if (found == null) return null;
        String place = (value.substring(0, found.start()) + " " + value.substring(found.end())).replaceAll("(?iu)\\b(in|at|place|ort|date|datum|am)\\b", " ").replaceAll("[,;:()]", " ").replaceAll("\\s+", " ").strip();
        return found.date().format(YYMMDD) + place.toUpperCase(Locale.ROOT);
    }

    private static final Pattern CURRENCY_FIRST = Pattern.compile("(?i)\\b([A-Z]{3})\\b\\s*([0-9][0-9.,'  ]*[0-9]|[0-9])");
    private static final Pattern CURRENCY_LAST = Pattern.compile("(?i)([0-9][0-9.,'  ]*[0-9]|[0-9])\\s*\\b([A-Z]{3})\\b");

    static String amount(String value) {
        String text = value.replace('\n', ' ');
        String currency = null, number = null;
        var first = CURRENCY_FIRST.matcher(text);
        if (first.find() && isCurrency(first.group(1))) { currency = first.group(1).toUpperCase(Locale.ROOT); number = first.group(2); }
        else {
            var last = CURRENCY_LAST.matcher(text);
            if (last.find() && isCurrency(last.group(2))) { currency = last.group(2).toUpperCase(Locale.ROOT); number = last.group(1); }
        }
        if (currency == null) return null;
        String decimal = decimal(number);
        return decimal == null ? null : currency + decimal;
    }

    private static boolean isCurrency(String code) {
        try { Currency.getInstance(code.toUpperCase(Locale.ROOT)); return true; } catch (IllegalArgumentException unknown) { return false; }
    }

    /** "125.000,00", "125,000.00", "125 000,50", "125000" -> "125000,00" / "125000,50" / "125000,". */
    private static String decimal(String raw) {
        String s = raw.replaceAll("[\\s' ]", "");
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        int sep = Math.max(comma, dot);
        String integer, fraction = "";
        if (sep >= 0 && s.length() - sep - 1 <= 2) {
            integer = s.substring(0, sep).replaceAll("[.,]", ""); fraction = s.substring(sep + 1);
        } else integer = s.replaceAll("[.,]", "");
        if (integer.isEmpty() || !integer.matches("\\d+")) return null;
        try { new BigDecimal(integer + (fraction.isEmpty() ? "" : "." + fraction)); } catch (NumberFormatException e) { return null; }
        return integer + "," + fraction;
    }
}
