package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.LetterOfCredit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AmendmentSnapshotTest {
    @Test void capturesIndependentBusinessState() throws Exception {
        LetterOfCredit lc=new LetterOfCredit();
        lc.setReference("LC-123");lc.setAmount(new BigDecimal("100.00"));
        lc.setRequiredDocuments(new ArrayList<>(List.of("INVOICE")));
        String before=AmendmentSnapshot.capture(lc);
        lc.setAmount(new BigDecimal("200.00"));lc.getRequiredDocuments().clear();
        var parsed=new ObjectMapper().readTree(before);
        assertThat(parsed.get("amount").asText()).isEqualTo("100.00");
        assertThat(parsed.get("requiredDocuments").get(0).asText()).isEqualTo("INVOICE");
        assertThat(AmendmentSnapshot.capture(lc)).isNotEqualTo(before);
    }
}
