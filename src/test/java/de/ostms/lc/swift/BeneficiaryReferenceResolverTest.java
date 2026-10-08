package de.ostms.lc.swift;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class BeneficiaryReferenceResolverTest {
    @Test void resolvesExplicitAddressAndPreservesOriginalMessage() {
        String raw=":20:REF\n:59:SEE FIELD 47A\n:47A:1. BENEFICIARY NAME AND ADDRESS:\nACME GMBH\nHAUPTSTRASSE 1\nBERLIN, GERMANY\n2. ALL DOCUMENTS IN ENGLISH";
        var lc=new Mt700Parser().parse(raw);
        assertThat(lc.getBeneficiary()).isEqualTo("ACME GMBH\nHAUPTSTRASSE 1\nBERLIN, GERMANY");
        assertThat(lc.getRawMessage()).isEqualTo(raw);
    }
    @Test void leavesNormalAddressUntouched() {
        assertThat(BeneficiaryReferenceResolver.resolve(Map.of("59","ACME GMBH")).value()).isEqualTo("ACME GMBH");
    }
    @Test void neverCopiesUnlabelledConditions() {
        var result=BeneficiaryReferenceResolver.resolve(Map.of("59","SEE FIELD 47A","47A","DOCUMENTS MUST BE IN ENGLISH"));
        assertThat(result.resolved()).isFalse();
        assertThat(result.value()).isEqualTo("SEE FIELD 47A");
    }
    @Test void rejectsAmbiguousAndMissingReferences() {
        assertThat(BeneficiaryReferenceResolver.resolve(Map.of("59","SEE FIELD 47A")).resolved()).isFalse();
        assertThat(BeneficiaryReferenceResolver.resolve(Map.of("59","SEE FIELD 47A","47A","BENEFICIARY: ONE\n\nBENEFICIARY: TWO")).resolved()).isFalse();
        assertThat(BeneficiaryReferenceResolver.resolve(Map.of("59","SEE FIELD 47A AND SEE FIELD 46A","47A","BENEFICIARY: ONE")).resolved()).isFalse();
    }
    @Test void supportsGermanReferenceAndStopsAtNextSection() {
        var result=BeneficiaryReferenceResolver.resolve(Map.of("59","siehe Feld :47A:","47A","BEGÜNSTIGTER: ACME\nBERLIN\nDOCUMENTS: IN ENGLISH"));
        assertThat(result.value()).isEqualTo("ACME\nBERLIN");
        assertThat(result.source()).isEqualTo("47A");
    }
}
