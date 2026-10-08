package de.ostms.lc.lc.service;

import de.ostms.lc.lc.domain.*;
import de.ostms.lc.lc.repository.*;
import de.ostms.lc.swift.Mt707Parser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmendmentImportTest {
    private final AmendmentRepository amendments=mock(AmendmentRepository.class);
    private final LetterOfCreditRepository lcs=mock(LetterOfCreditRepository.class);
    private final AmendmentService service=new AmendmentService(amendments,lcs,new Mt707Parser());
    private LetterOfCredit lc(){
        var lc=new LetterOfCredit();lc.setReference("LC-123");lc.setAmount(new BigDecimal("100.00"));
        when(lcs.findForAmendment("LC-123")).thenReturn(Optional.of(lc));
        when(amendments.save(any())).thenAnswer(i->i.getArgument(0));return lc;
    }
    @Test void storesBothStatesAndConsolidatesAmount(){
        var lc=lc();var amendment=service.importMt707(":20:LC-123\n:26E:1\n:32B:EUR50,00");
        assertThat(lc.getAmount()).isEqualByComparingTo("150.00");
        assertThat(amendment.getBeforeState()).contains("\"amount\":\"100.00\"");
        assertThat(amendment.getAfterState()).contains("\"amount\":\"150.00\"");
    }
    @Test void rejectsDuplicateBeforeChangingLc(){
        var lc=lc();when(amendments.existsByLetterOfCreditIdAndAmendmentNumber(null,"1")).thenReturn(true);
        assertThatThrownBy(()->service.importMt707(":20:LC-123\n:26E:1\n:32B:EUR50,00")).isInstanceOf(IllegalArgumentException.class);
        assertThat(lc.getAmount()).isEqualByComparingTo("100.00");verify(amendments,never()).save(any());
    }
    @Test void rejectsMissingNumber(){lc();assertThatThrownBy(()->service.importMt707(":20:LC-123\n:32B:EUR50,00")).isInstanceOf(IllegalArgumentException.class);verify(lcs,never()).save(any());}
    @Test void preservesExplicitEmptyGoodsOverride(){
        var lc=lc();lc.setRawMessage(":45A:OLD GOODS");service.applyGoodsChanges(lc,"/DELETE/OLD GOODS");
        assertThat(lc.getAdditionalFields()).containsEntry("45A - gültige Warenbeschreibung","");
    }
}
