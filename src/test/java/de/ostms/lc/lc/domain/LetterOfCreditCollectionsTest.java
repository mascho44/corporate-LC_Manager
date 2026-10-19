package de.ostms.lc.lc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LetterOfCreditCollectionsTest {
    @Test void collectionsFromParsersAreCopiedIntoMutableOnes() {
        var lc = new LetterOfCredit();
        lc.setRequiredDocuments(List.of("Invoice", "Packing list"));
        lc.setAdditionalFields(Map.of("47A", "text"));
        lc.getRequiredDocuments().clear();
        lc.getAdditionalFields().clear();
        assertThat(lc.getRequiredDocuments()).isEmpty();
        assertThat(lc.getAdditionalFields()).isEmpty();
    }

    @Test void nullBecomesEmptyAndTheCallersListIsNotShared() {
        var lc = new LetterOfCredit();
        lc.setRequiredDocuments(null);
        lc.setAdditionalFields(null);
        assertThat(lc.getRequiredDocuments()).isEmpty();
        assertThat(lc.getAdditionalFields()).isEmpty();
        var source = new java.util.ArrayList<>(List.of("A"));
        lc.setRequiredDocuments(source);
        source.add("B");
        assertThat(lc.getRequiredDocuments()).containsExactly("A");
    }
}
