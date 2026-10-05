package de.corporate.lc.check.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class RulePackEngineTest {
 private RulePackEngine.Input input(String rules,String currency) {
  return new RulePackEngine.Input(rules,"EUR",List.of(
   new RulePackEngine.Fact("synthetic-invoice.pdf","commercial-invoice",currency,"Metadaten")));
 }
 @Test void unknownAndOtherRulesAreNotAssumed() {
  for(String rules:new String[]{"","OTHER","EUCP LATEST VERSION","UCP 600 EXCEPT ARTICLE 18"}) {
   assertThat(RulePackEngine.evaluate(input(rules,"EUR")).get(0).code()).isEqualTo("RULE_PACK_NOT_APPLICABLE");
  }
 }
 @Test void matchesAndDifferencesRemainPartialHumanPrechecks() {
  for(String currency:new String[]{"EUR","USD",""}) {
   var finding=RulePackEngine.evaluate(input("UCP LATEST VERSION",currency)).get(0);
   assertThat(finding.code()).isEqualTo("UCP18_INVOICE_CURRENCY");
   assertThat(finding.severity().name()).isEqualTo("WARNING");
   assertThat(finding.rule().basis()).contains("18(a)(iii)");
  }
 }
 @Test void missingCurrencyIsNotSuccessAndRunsAreDeterministic() {
  var request=input("UCP 600",null);
  assertThat(RulePackEngine.evaluate(request)).isEqualTo(RulePackEngine.evaluate(request));
  assertThat(RulePackEngine.evaluate(request).get(0).message()).contains("nicht prüfbar");
 }
 @Test void plannedPacksDoNotPretendToHaveRules() {
  assertThat(RulePacks.all()).hasSize(6);
  assertThat(RulePacks.all().stream().filter(p->p.status().equals("PLANNED")))
   .allMatch(p->p.rules().isEmpty());
 }
}
