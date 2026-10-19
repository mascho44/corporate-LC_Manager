package de.ostms.lc.document.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** "EUR ONE HUNDRED TWENTY-FIVE THOUSAND AND 50/100 ONLY": the amount in words as trade documents print it. */
public final class AmountInWords {
    private static final String[] SMALL = {"ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
    private static final String[] TENS = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY", "SIXTY", "SEVENTY", "EIGHTY", "NINETY"};
    private static final String[] SCALE = {"", " THOUSAND", " MILLION", " BILLION", " TRILLION"};

    private AmountInWords() {}

    public static String english(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) return "-";
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        long whole = rounded.toBigInteger().longValueExact();
        int cents = rounded.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String code = currency == null || currency.isBlank() ? "" : currency.trim().toUpperCase() + " ";
        return code + words(whole) + (cents == 0 ? " ONLY" : " AND " + String.format("%02d", cents) + "/100 ONLY");
    }

    static String words(long n) {
        if (n == 0) return SMALL[0];
        var out = new StringBuilder();
        int scale = 0;
        while (n > 0) {
            int part = (int) (n % 1000);
            if (part != 0) out.insert(0, below1000(part) + SCALE[scale] + (out.length() > 0 ? " " : ""));
            n /= 1000;
            scale++;
        }
        return out.toString().trim();
    }

    private static String below1000(int n) {
        var out = new StringBuilder();
        if (n >= 100) { out.append(SMALL[n / 100]).append(" HUNDRED"); n %= 100; if (n > 0) out.append(" "); }
        if (n >= 20) { out.append(TENS[n / 10]); if (n % 10 > 0) out.append("-").append(SMALL[n % 10]); }
        else if (n > 0) out.append(SMALL[n]);
        return out.toString();
    }
}
