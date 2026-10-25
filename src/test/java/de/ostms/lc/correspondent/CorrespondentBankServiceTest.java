package de.ostms.lc.correspondent;

import de.ostms.lc.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CorrespondentBankServiceTest {
 private final CorrespondentBankRepository banks=mock(CorrespondentBankRepository.class);
 private final AuditService audit=mock(AuditService.class);
 private final CorrespondentBankService service=new CorrespondentBankService(banks,audit);
 private static CorrespondentBankService.Request request(String bic,List<String> channels,String preferred){
  return new CorrespondentBankService.Request(bic,"Beispielbank AG","de","Frankfurt",channels,preferred,null,"swift@example.com","+49 69 1",null,null);
 }

 @Test void createsWithNormalisedValuesAndAudits(){
  when(banks.save(any())).thenAnswer(i->i.getArgument(0));
  var view=service.create(request(" deutdeff ",List.of("swift","ebics"),"ebics"),null);
  assertThat(view.bic()).isEqualTo("DEUTDEFF");assertThat(view.country()).isEqualTo("DE");assertThat(view.channels()).containsExactly("SWIFT","EBICS");
  assertThat(view.preferredChannel()).isEqualTo("EBICS");assertThat(view.active()).isTrue();
  verify(audit).recordInTransaction(any(),eq("CORRESPONDENT_BANK_CREATED"),eq("CORRESPONDENT_BANK"),any(UUID.class),anyString());
 }
 @Test void rejectsInvalidInput(){
  assertThatThrownBy(()->service.create(request("DEUT",List.of(),null),null)).hasMessageContaining("BIC");
  assertThatThrownBy(()->service.create(request("DEUTDEFF",List.of("FAX"),null),null)).hasMessageContaining("Kommunikationsweg");
  assertThatThrownBy(()->service.create(request("DEUTDEFF",List.of("SWIFT"),"EMAIL"),null)).hasMessageContaining("bevorzugte");
  assertThatThrownBy(()->service.create(new CorrespondentBankService.Request("DEUTDEFF"," ","DE",null,List.of(),null,null,null,null,null,null),null)).hasMessageContaining("Namen");
  assertThatThrownBy(()->service.create(new CorrespondentBankService.Request("DEUTDEFF","X","DEU",null,List.of(),null,null,null,null,null,null),null)).hasMessageContaining("Ländercode");
  assertThatThrownBy(()->service.create(new CorrespondentBankService.Request("DEUTDEFF","X","DE",null,List.of(),null,null,"keine-adresse",null,null,null),null)).hasMessageContaining("E-Mail");
 }
 @Test void bicIsUniquePerTenantButMayBeKeptOnUpdate(){
  var existing=new CorrespondentBank();existing.set("DEUTDEFF","Alt","DE",null,List.of(),null,null,null,null,null,true,"t");
  when(banks.byBic("DEUTDEFF")).thenReturn(Optional.of(existing));
  assertThatThrownBy(()->service.create(request("DEUTDEFF",List.of(),null),null)).hasMessageContaining("existiert bereits");
  when(banks.findById(existing.getId())).thenReturn(Optional.of(existing));when(banks.save(any())).thenAnswer(i->i.getArgument(0));
  assertThat(service.update(existing.getId(),request("DEUTDEFF",List.of("EMAIL"),"EMAIL"),null).channels()).containsExactly("EMAIL");
 }
 @Test void unknownBankOnUpdateOrDeleteIsNotFound(){
  when(banks.findById(any())).thenReturn(Optional.empty());
  assertThatThrownBy(()->service.update(UUID.randomUUID(),request("DEUTDEFF",List.of(),null),null)).isInstanceOf(NoSuchElementException.class);
  assertThatThrownBy(()->service.delete(UUID.randomUUID(),null)).isInstanceOf(NoSuchElementException.class);
 }
}
