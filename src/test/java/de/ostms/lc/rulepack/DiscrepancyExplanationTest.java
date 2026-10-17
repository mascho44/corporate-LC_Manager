package de.ostms.lc.rulepack;

import static org.junit.jupiter.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.*;
import org.junit.jupiter.api.Test;

class DiscrepancyExplanationTest {
    private Rule rule(Field left, Operator op, Field right) {
        return new Rule("r", "1.0.0", de.ostms.lc.document.domain.DocumentType.BILL_OF_LADING, left, op, right, Level.DISCREPANCY, "m", "s");
    }

    @Test
    void datesNameTheDaysAfterTheLimit() {
        var text = DiscrepancyExplanation.of(rule(Field.DOCUMENT_SHIPMENT_DATE, Operator.LTE, Field.LC_LATEST_SHIPMENT_DATE), "2026-09-18", "2026-09-15");
        assertTrue(text.orElseThrow().contains("3 Tage nach der zulässigen Grenze"), text.toString());
        assertTrue(text.get().contains("18.09.2026") && text.get().contains("15.09.2026"));
    }

    @Test
    void satisfiedOrUnreadableComparisonsGiveNothing() {
        var r = rule(Field.DOCUMENT_SHIPMENT_DATE, Operator.LTE, Field.LC_LATEST_SHIPMENT_DATE);
        assertTrue(DiscrepancyExplanation.of(r, "2026-09-10", "2026-09-15").isEmpty());
        assertTrue(DiscrepancyExplanation.of(r, "kein Datum", "2026-09-15").isEmpty());
        assertTrue(DiscrepancyExplanation.of(r, null, "2026-09-15").isEmpty());
    }

    @Test
    void numbersNameTheExcess() {
        var text = DiscrepancyExplanation.of(rule(Field.DOCUMENT_AMOUNT, Operator.LTE, Field.LC_AMOUNT), "131500", "125000");
        assertTrue(text.orElseThrow().contains("6.500"), text.toString());
        assertTrue(text.get().contains("über dem Höchstwert"));
    }
}
