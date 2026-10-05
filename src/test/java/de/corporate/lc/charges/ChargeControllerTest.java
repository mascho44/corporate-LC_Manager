package de.corporate.lc.charges;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.lc.domain.LetterOfCredit;
import de.corporate.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ChargeControllerTest {
 @Test void storesPinnedProfileAndRoundedResultAndAudits() throws Exception {
  var profiles=mock(ChargeProfileRepository.class);var estimates=mock(ChargeEstimateRepository.class);
  var lcs=mock(LetterOfCreditRepository.class);var audit=mock(AuditService.class);var json=new ObjectMapper().findAndRegisterModules();
  var controller=new ChargeController(profiles,estimates,lcs,json,audit);
  var id=UUID.randomUUID();var lc=new LetterOfCredit();lc.setCurrency("EUR");lc.setAmount(new BigDecimal("1000"));
  var p=new ChargeProfile();p.currency="EUR";p.name="Synthetic tariff";
  p.rulesJson=json.writeValueAsString(List.of(new ChargeRule(ChargeType.OPENING,new BigDecimal("1"),BigDecimal.ZERO,BigDecimal.ZERO,null)));
  when(lcs.findById(id)).thenReturn(Optional.of(lc));when(profiles.findById(p.id)).thenReturn(Optional.of(p));
  var auth=new UsernamePasswordAuthenticationToken("synthetic-user","unused");
  var result=controller.estimate(id,new ChargeController.EstimateRequest(p.id,Map.of(ChargeType.OPENING,2)),auth);
  assertThat(json.readTree(result.resultJson).get("total").decimalValue()).isEqualByComparingTo("20.00");
  assertThat(json.readTree(result.profileSnapshot).get("name").asText()).isEqualTo("Synthetic tariff");
  p.name="Changed later";assertThat(result.profileSnapshot).doesNotContain("Changed later");
  verify(estimates).save(result);verify(audit).recordInTransaction(eq(auth),eq("LC_CHARGES_ESTIMATED"),eq("LETTER_OF_CREDIT"),eq(id),anyString());
  p.currency="USD";
  assertThatThrownBy(()->controller.estimate(id,new ChargeController.EstimateRequest(p.id,Map.of(ChargeType.OPENING,1)),auth)).isInstanceOf(IllegalArgumentException.class);
 }
}
