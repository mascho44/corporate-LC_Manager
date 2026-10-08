package de.ostms.lc.document.service;

import de.ostms.lc.document.api.DocumentInboxItemView;
import de.ostms.lc.document.domain.DocumentInboxItem;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository.AssignmentTarget;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LcAssignmentMatcherTest {
    @Test void exactReferenceIsCaseInsensitiveAndExplained(){
        var result=LcAssignmentMatcher.suggest(item("lc1234",null),List.of(target("LC1234"),target("LC9999")));
        assertThat(result).hasSize(1);assertThat(result.get(0).reason()).isEqualTo("REFERENCE_EXACT");assertThat(result.get(0).evidence()).isEqualTo("lc1234");
    }
    @Test void punctuationDifferencesSuggestAllAmbiguousTransactions(){
        DocumentInboxItem item=item("lc1234",null);
        var result=LcAssignmentMatcher.suggest(item,List.of(target("LC-1234"),target("LC/1234")));
        assertThat(result).hasSize(2);assertThat(result).extracting("reason").containsOnly("REFERENCE_NORMALIZED");
        assertThat(DocumentInboxItemView.from(item,result).suggestedLcId()).isNull();
    }
    @Test void scansTextEvenWhenExtractionDidNotFindReference(){
        var result=LcAssignmentMatcher.suggest(item(null,"Invoice 17, documentary credit: LC2026/4711. Payment terms apply."),List.of(target("LC2026/4711")));
        assertThat(result).hasSize(1);assertThat(result.get(0).reason()).isEqualTo("REFERENCE_IN_TEXT");assertThat(result.get(0).evidence()).contains("LC2026/4711");
    }
    @Test void matchesOcrSpacingAndSeparatorsWithReviewReason(){
        var result=LcAssignmentMatcher.suggest(item(null,"Letter of credit: LC 2026 - 4711"),List.of(target("LC2026/4711")));
        assertThat(result).hasSize(1);assertThat(result.get(0).reason()).isEqualTo("REFERENCE_FORMATTED_IN_TEXT");
    }
    @Test void doesNotMatchSubstringOfAnotherReference(){
        for(String text:List.of("LC12345","OTHERLC1234","LC1234/A","LC1234.99"))
            assertThat(LcAssignmentMatcher.suggest(item(null,text),List.of(target("LC1234")))).as(text).isEmpty();
    }
    @Test void preservesMultipleTextReferencesAndRanksExplicitReferenceFirst(){
        var result=LcAssignmentMatcher.suggest(item("LC9999","Original credit LC1234, related credit LC9999."),List.of(target("LC1234"),target("LC9999")));
        assertThat(result).extracting("reference").containsExactly("LC9999","LC1234");
    }
    @Test void noReferenceMeansManualAssignment(){
        assertThat(LcAssignmentMatcher.suggest(item(null,"An invoice without any credit reference"),List.of(target("LC1234")))).isEmpty();
    }
    private DocumentInboxItem item(String reference,String text){var item=new DocumentInboxItem();item.setExtractedReference(reference);item.setExtractedText(text);return item;}
    private AssignmentTarget target(String reference){var target=mock(AssignmentTarget.class);when(target.getId()).thenReturn(UUID.randomUUID());when(target.getReference()).thenReturn(reference);when(target.getStatus()).thenReturn(LetterOfCreditStatus.ACTIVE);return target;}
}
