package de.corporate.lc.lc.service;
import de.corporate.lc.lc.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LcConditionsTest {
 @Test void canonicalValuesWorkWithoutAnySwiftSource(){var lc=new LetterOfCredit();lc.getConditions().put(LcCondition.GOODS_DESCRIPTION,"PUMPS");assertThat(LcConditions.value(lc,LcCondition.GOODS_DESCRIPTION)).contains("PUMPS");}
 @Test void correctedLegacyValuesTakePrecedenceOverOriginal(){var lc=new LetterOfCredit();lc.setRawMessage(":45A:OLD\n:47A:TERMS");lc.getAdditionalFields().put("45A - Warenbeschreibung","CORRECTED");assertThat(LcConditions.value(lc,LcCondition.GOODS_DESCRIPTION)).contains("CORRECTED");assertThat(LcConditions.value(lc,LcCondition.ADDITIONAL_CONDITIONS)).contains("TERMS");}
 @Test void canonicalOverrideAndExplicitBlankSuppressLegacyValues(){var lc=new LetterOfCredit();lc.setRawMessage(":45A:OLD");lc.getConditions().put(LcCondition.GOODS_DESCRIPTION,"NEW");assertThat(LcConditions.value(lc,LcCondition.GOODS_DESCRIPTION)).contains("NEW");lc.getConditions().put(LcCondition.GOODS_DESCRIPTION,"");assertThat(LcConditions.value(lc,LcCondition.GOODS_DESCRIPTION)).isEmpty();}
 @Test void unrelatedLegacyFieldNameIsNotMistakenForSwiftTag(){var lc=new LetterOfCredit();lc.getAdditionalFields().put("Explanation about 45A","WRONG");assertThat(LcConditions.value(lc,LcCondition.GOODS_DESCRIPTION)).isEmpty();}
}
