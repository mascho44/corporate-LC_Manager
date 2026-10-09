package de.ostms.lc.lc.service;

import de.ostms.lc.lc.api.LetterOfCreditUpdateRequest;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WaitingForCustomerTest {
 private LetterOfCreditUpdateRequest request(LetterOfCreditStatus status,LocalDate followUp){
  return new LetterOfCreditUpdateRequest(null,null,"REF",null,null,null,null,null,null,null,null,null,null,null,null,null,followUp,status,null,null);
 }
 @Test void enteringWaitingStampsDateAndDefaultsFollowUpLeavingTheStateClearsIt(){
  var repo=mock(LetterOfCreditRepository.class);var lc=new LetterOfCredit();lc.setStatus(LetterOfCreditStatus.ACTIVE);
  when(repo.findById(any())).thenReturn(Optional.of(lc));when(repo.existsByReferenceAndIdNot(any(),any())).thenReturn(false);
  var service=new LetterOfCreditService(repo,null,null);var id=UUID.randomUUID();
  service.update(id,request(LetterOfCreditStatus.WAITING_FOR_CUSTOMER,null));
  assertThat(lc.getWaitingSince()).isEqualTo(LocalDate.now());assertThat(lc.getFollowUpDate()).isEqualTo(LocalDate.now().plusDays(7));
  var stamp=lc.getWaitingSince();
  service.update(id,request(LetterOfCreditStatus.WAITING_FOR_CUSTOMER,LocalDate.now().plusDays(3)));
  assertThat(lc.getWaitingSince()).isEqualTo(stamp);assertThat(lc.getFollowUpDate()).isEqualTo(LocalDate.now().plusDays(3));
  service.update(id,request(LetterOfCreditStatus.ACTIVE,null));
  assertThat(lc.getWaitingSince()).isNull();
 }
}
