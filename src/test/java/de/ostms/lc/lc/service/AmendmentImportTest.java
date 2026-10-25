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
    private Amendment accepted(LetterOfCredit lc,String raw){
        var amendment=service.importMt707(raw);
        org.springframework.test.util.ReflectionTestUtils.setField(amendment,"id",java.util.UUID.randomUUID());
        when(amendments.findById(amendment.getId())).thenReturn(Optional.of(amendment));
        return service.accept(amendment.getId(),"anna","passt");
    }
    @Test void importOnlyStoresAPendingAmendmentAndLeavesTheCreditUntouched(){
        var lc=lc();var amendment=service.importMt707(":20:LC-123\n:26E:1\n:32B:EUR50,00\n:31E:271231BERLIN");
        assertThat(amendment.getStatus()).isEqualTo("PENDING");assertThat(amendment.isPending()).isTrue();
        assertThat(lc.getAmount()).isEqualByComparingTo("100.00");assertThat(lc.getExpiryDate()).isNull();
        assertThat(amendment.getBeforeState()).isNull();verify(lcs,never()).save(any());
    }
    @Test void acceptingAppliesTheChangesAndStoresBothStates(){
        var lc=lc();var amendment=accepted(lc,":20:LC-123\n:26E:1\n:32B:EUR50,00");
        assertThat(lc.getAmount()).isEqualByComparingTo("150.00");assertThat(amendment.getStatus()).isEqualTo("ACCEPTED");
        assertThat(amendment.getBeforeState()).contains("\"amount\":\"100.00\"");assertThat(amendment.getAfterState()).contains("\"amount\":\"150.00\"");
        assertThat(amendment.getDecidedBy()).isEqualTo("anna");assertThat(amendment.getDecisionComment()).isEqualTo("passt");assertThat(amendment.getDecidedAt()).isNotNull();
        assertThatThrownBy(()->service.accept(amendment.getId(),"ben",null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("bereits entschieden");
        assertThatThrownBy(()->service.reject(amendment.getId(),"ben",null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectingKeepsTheCreditAsItIs(){
        var lc=lc();var amendment=service.importMt707(":20:LC-123\n:26E:1\n:32B:EUR50,00");
        org.springframework.test.util.ReflectionTestUtils.setField(amendment,"id",java.util.UUID.randomUUID());when(amendments.findById(amendment.getId())).thenReturn(Optional.of(amendment));
        var rejected=service.reject(amendment.getId(),"anna","nicht vereinbart");
        assertThat(rejected.getStatus()).isEqualTo("REJECTED");assertThat(lc.getAmount()).isEqualByComparingTo("100.00");verify(lcs,never()).save(any());
    }
    @Test void anEarlierOpenAmendmentMustBeDecidedFirst(){
        var lc=lc();
        var first=service.importMt707(":20:LC-123\n:26E:1\n:32B:EUR10,00");var second=service.importMt707(":20:LC-123\n:26E:2\n:32B:EUR20,00");
        for(var a:new Amendment[]{first,second})org.springframework.test.util.ReflectionTestUtils.setField(a,"id",java.util.UUID.randomUUID());
        when(amendments.findById(first.getId())).thenReturn(Optional.of(first));when(amendments.findById(second.getId())).thenReturn(Optional.of(second));
        when(amendments.findByLetterOfCreditIdOrderByImportedAtDesc(any())).thenReturn(java.util.List.of(second,first));
        assertThatThrownBy(()->service.accept(second.getId(),"anna",null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Nr. 1 ist noch offen");
        assertThat(lc.getAmount()).isEqualByComparingTo("100.00");
        service.reject(first.getId(),"anna","nein");
        assertThat(service.accept(second.getId(),"anna",null).getStatus()).isEqualTo("ACCEPTED");assertThat(lc.getAmount()).isEqualByComparingTo("120.00");
    }
    @Test void numbersAreComparedNumerically(){
        assertThat(AmendmentService.number("2")).hasValue(2);assertThat(AmendmentService.number("010")).hasValue(10);assertThat(AmendmentService.number("A")).isEmpty();assertThat(AmendmentService.number(null)).isEmpty();
    }
    @Test void theCreditNumberMayStandInField21Or23Or20(){
        var lc=lc();
        for(String raw:new String[]{":20:BANKREF\n:21:LC-123\n:26E:1",":20:BANKREF\n:23:LC-123\n:26E:1",":20:LC-123\n:26E:1"}){
            var a=service.importMt707(raw);assertThat(a.getLetterOfCredit()).as(raw).isSameAs(lc);
        }
        assertThatThrownBy(()->service.importMt707(":20:OTHER\n:21:ALSO-OTHER\n:26E:1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OTHER").hasMessageContaining("ALSO-OTHER");
    }
    @Test void referencesPointingToDifferentDossiersAreAnError(){
        var lc=lc();var other=new LetterOfCredit();other.setReference("LC-999");when(lcs.findForAmendment("LC-999")).thenReturn(Optional.of(other));
        assertThatThrownBy(()->service.importMt707(":20:LC-999\n:21:LC-123\n:26E:1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mehreren Akten");
        assertThat(lc.getAmount()).isEqualByComparingTo("100.00");
    }
    @Test void aMessageWithoutAnyReferenceIsRejected(){
        lc();assertThatThrownBy(()->service.importMt707(":26E:1\n:32B:EUR1,00")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(":21:");
    }
    @Test void beneficiaryAndOtherFieldChangesAreAppliedOnAcceptance(){
        var lc=lc();lc.setBeneficiary("OLD SUPPLIER\nOLD ROAD");
        var amendment=accepted(lc,":20:LC-123\n:26E:1\n:59:NEW SUPPLIER LTD\nNEW ROAD 5\nSHANGHAI\n:39A:10/10\n:44E:NINGBO");
        assertThat(lc.getBeneficiary()).startsWith("NEW SUPPLIER LTD");
        assertThat(lc.getAdditionalFields()).containsEntry("44E - "+de.ostms.lc.swift.Mt700Parser.label("44E"),"NINGBO").containsEntry("39A - "+de.ostms.lc.swift.Mt700Parser.label("39A"),"10/10");
        assertThat(amendment.getAfterState()).contains("NEW SUPPLIER LTD");assertThat(amendment.getBeforeState()).contains("OLD SUPPLIER");
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
