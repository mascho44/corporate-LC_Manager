package de.ostms.lc.check.service;

import de.ostms.lc.check.api.CheckResult;
import de.ostms.lc.check.api.CheckResult.Severity;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyNoticeTest {
    @Test void onlyConfirmedDiscrepanciesInSwiftCharacterSetAndShortLines() {
        var lc=new LetterOfCredit();lc.setReference("ILC250B000001");lc.setCurrency("EUR");lc.setAmount(new BigDecimal("250000"));
        var confirmed=new CheckResult(Severity.DISCREPANCY,"INVOICE_AMOUNT_EXCEEDED","Rechnungsbetrag übersteigt den Akkreditivbetrag – Überziehung um EUR 1.500,00 festgestellt, bitte prüfen „sofort“","EUR 250000","invoice.pdf","EUR 251500","CONFIRMED_DISCREPANCY","Original geprüft","anna",null);
        var accepted=new CheckResult(Severity.OK,"GOODS","Ware ok",null,"invoice.pdf",null,"ACCEPTED","x","anna",null);
        var open=new CheckResult(Severity.DISCREPANCY,"PORT","Hafen weicht ab");
        var draft=DiscrepancyNotice.create(lc,List.of(confirmed,accepted,open),LocalDate.of(2026,10,15));
        assertThat(draft.discrepancies()).isEqualTo(1);
        assertThat(draft.mt734()).startsWith(":20:ILC250B000001\n:21:NONREF\n:32A:261015EUR250000,00\n:77J:1. INVOICE.PDF: RECHNUNGSBETRAG UEBERSTEIGT");
        assertThat(draft.mt734()).contains(":77B:/HOLD/").doesNotContain("Ü","„","–");
        assertThat(draft.mt734().lines()).allMatch(l->l.length()<=5+DiscrepancyNotice.LINE);
        assertThat(draft.letter()).contains("Unstimmigkeiten zum Akkreditiv ILC250B000001","1. invoice.pdf: Rechnungsbetrag","Original geprüft").doesNotContain("Hafen");
    }
}
