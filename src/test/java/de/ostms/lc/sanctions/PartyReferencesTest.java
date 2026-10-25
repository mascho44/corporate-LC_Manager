package de.ostms.lc.sanctions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PartyReferencesTest {
    @Test void referencesToOtherPartiesAreNotNames() {
        for (String reference : new String[]{"APPLICANT", "Applicant", "THE APPLICANT", "SAME AS APPLICANT", "SAME AS CONSIGNEE", "TO ORDER", "TO ORDER OF SHIPPER", "TO THE ORDER OF ISSUING BANK", "ISSUING BANK", "NOT APPLICABLE", "N/A", "-", "", "  ", "APPLICANT."})
            assertThat(PartyReferences.isReference(reference)).as(reference).isTrue();
        assertThat(PartyReferences.isReference(null)).isFalse();
    }

    @Test void realNamesAreStillScreened() {
        for (String name : new String[]{"LLC APPLICATA", "APPLICANT TRADING GMBH", "ACME CORP", "ORDER FULFILMENT LTD", "THE BANK OF CHINA"})
            assertThat(PartyReferences.isReference(name)).as(name).isFalse();
    }
}
