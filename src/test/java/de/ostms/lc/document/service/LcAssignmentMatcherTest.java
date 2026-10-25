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

    private DocumentInboxItem part(UUID source,String name,String reference,String text){
        var item=item(reference,text);item.setOriginalFilename(name);item.setSourceInboxId(source);
        org.springframework.test.util.ReflectionTestUtils.setField(item,"id",UUID.randomUUID());return item;
    }
    private Map<UUID,List<de.ostms.lc.document.api.LcAssignmentCandidate>> own(List<DocumentInboxItem> items,List<AssignmentTarget> targets){
        var map=new HashMap<UUID,List<de.ostms.lc.document.api.LcAssignmentCandidate>>();items.forEach(i->map.put(i.getId(),LcAssignmentMatcher.suggest(i,targets)));return map;
    }
    @Test void partsWithoutReferenceInheritTheDossierFoundInAnotherPartOfTheSameFile(){
        var source=UUID.randomUUID();var targets=List.of(target("LC1234"),target("LC9999"));
        var invoice=part(source,"scan-Seiten-1-2.pdf",null,"Invoice. Documentary credit LC1234");
        var packing=part(source,"scan-Seiten-3-4.pdf",null,"Packing list without any reference");
        var other=part(UUID.randomUUID(),"other-Seiten-1-1.pdf",null,"Bill of lading");
        var all=List.of(invoice,packing,other);
        var result=LcAssignmentMatcher.withSiblingSuggestions(all,own(all,targets));
        assertThat(result.get(invoice.getId())).hasSize(1).extracting("reason").containsExactly("REFERENCE_IN_TEXT");
        assertThat(result.get(packing.getId())).hasSize(1);
        var inherited=result.get(packing.getId()).get(0);
        assertThat(inherited.reference()).isEqualTo("LC1234");assertThat(inherited.reason()).isEqualTo("SIBLING_SOURCE");assertThat(inherited.evidence()).contains("scan-Seiten-1-2.pdf");
        assertThat(DocumentInboxItemView.from(packing,result.get(packing.getId())).suggestedLcId()).isNotNull();
        assertThat(result.get(other.getId())).as("a different source file gets nothing").isEmpty();
    }
    @Test void conflictingDossiersInOneFileAreNotPropagated(){
        var source=UUID.randomUUID();var targets=List.of(target("LC1234"),target("LC9999"));
        var a=part(source,"a.pdf",null,"credit LC1234");var b=part(source,"b.pdf",null,"credit LC9999");var c=part(source,"c.pdf",null,"nothing");
        var all=List.of(a,b,c);var result=LcAssignmentMatcher.withSiblingSuggestions(all,own(all,targets));
        assertThat(result.get(c.getId())).isEmpty();assertThat(result.get(a.getId())).hasSize(1);assertThat(result.get(b.getId())).hasSize(1);
    }
    @Test void existingSuggestionsAreNeverReplacedAndSinglePartsAreIgnored(){
        var source=UUID.randomUUID();var targets=List.of(target("LC1234"));
        var a=part(source,"a.pdf",null,"credit LC1234");var b=part(source,"b.pdf","LC1234","text");
        var alone=part(UUID.randomUUID(),"alone.pdf",null,"x");
        var all=List.of(a,b,alone);var result=LcAssignmentMatcher.withSiblingSuggestions(all,own(all,targets));
        assertThat(result.get(b.getId()).get(0).reason()).isEqualTo("REFERENCE_EXACT");assertThat(result.get(alone.getId())).isEmpty();
    }
    @Test void itemsWithoutASourceAreUntouched(){
        var targets=List.of(target("LC1234"));var a=part(null,"a.pdf",null,"credit LC1234");var b=part(null,"b.pdf",null,"nothing");
        var all=List.of(a,b);var result=LcAssignmentMatcher.withSiblingSuggestions(all,own(all,targets));
        assertThat(result.get(b.getId())).isEmpty();
    }
}
