package de.ostms.lc.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountInWordsTest {
    @Test void printsWholeAndCentAmounts() {
        assertThat(AmountInWords.english(new BigDecimal("125000.00"), "eur")).isEqualTo("EUR ONE HUNDRED TWENTY-FIVE THOUSAND ONLY");
        assertThat(AmountInWords.english(new BigDecimal("131500.5"), "EUR")).isEqualTo("EUR ONE HUNDRED THIRTY-ONE THOUSAND FIVE HUNDRED AND 50/100 ONLY");
        assertThat(AmountInWords.english(new BigDecimal("1000000"), "USD")).isEqualTo("USD ONE MILLION ONLY");
        assertThat(AmountInWords.english(new BigDecimal("2001010.07"), null)).isEqualTo("TWO MILLION ONE THOUSAND TEN AND 07/100 ONLY");
        assertThat(AmountInWords.english(new BigDecimal("0.00"), "EUR")).isEqualTo("EUR ZERO ONLY");
    }

    @Test void refusesMissingOrNegativeAmounts() {
        assertThat(AmountInWords.english(null, "EUR")).isEqualTo("-");
        assertThat(AmountInWords.english(new BigDecimal("-1"), "EUR")).isEqualTo("-");
    }
}
