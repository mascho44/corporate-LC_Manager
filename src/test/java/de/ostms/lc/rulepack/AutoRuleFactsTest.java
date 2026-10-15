package de.ostms.lc.rulepack;

import de.ostms.lc.document.domain.LcDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AutoRuleFactsTest {
    @Test void fillsOnlyEmptyFieldsAndRemembersThem() {
        var suggester=mock(DocumentFactSuggester.class);
        var doc=new LcDocument();doc.setRuleFactsJson("{\"DOCUMENT_VESSEL\":\"MAERSK ESSEX\"}");
        when(suggester.suggest(doc)).thenReturn(List.of(
            new DocumentFactSuggester.Suggestion(Field.DOCUMENT_VESSEL,"OTHER VESSEL","OCR",null),
            new DocumentFactSuggester.Suggestion(Field.DOCUMENT_LOADING_PORT,"HAMBURG","OCR",null),
            new DocumentFactSuggester.Suggestion(Field.DOCUMENT_DISCHARGE_PORT," ","OCR",null),
            new DocumentFactSuggester.Suggestion(Field.DOCUMENT_ISSUER,"10 cartons gross weight","Aussteller im Briefkopf (erste Firma) erkannt – bitte prüfen",null)));
        var auto=new AutoRuleFacts();ReflectionTestUtils.setField(auto,"documents",suggester);
        assertThat(auto.apply(doc)).isEqualTo(1);
        var facts=RuleFacts.read(doc.getRuleFactsJson());
        assertThat(facts.get(Field.DOCUMENT_VESSEL)).isEqualTo("MAERSK ESSEX");
        assertThat(facts.get(Field.DOCUMENT_LOADING_PORT)).isEqualTo("HAMBURG");
        assertThat(facts).doesNotContainKey(Field.DOCUMENT_DISCHARGE_PORT).doesNotContainKey(Field.DOCUMENT_ISSUER);
        assertThat(AutoRuleFacts.read(doc.getAutoRuleFacts())).containsExactly("DOCUMENT_LOADING_PORT");
        // A second run adds nothing new and keeps the marker.
        assertThat(auto.apply(doc)).isZero();assertThat(AutoRuleFacts.read(doc.getAutoRuleFacts())).containsExactly("DOCUMENT_LOADING_PORT");
    }
}
