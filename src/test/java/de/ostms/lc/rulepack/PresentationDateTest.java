package de.ostms.lc.rulepack;

import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class PresentationDateTest {
 @Test void todayIsProposedWhenNoPresentationDateIsThereAndOtherFactsStay(){
  var lc=new LetterOfCredit();lc.setRuleFactsJson(RuleFacts.encode(Map.of(Field.LC_TOLERANCE_PERCENT,"5"),false));
  assertThat(PresentationDate.stamp(lc,LocalDate.of(2026,10,14))).isTrue();
  var facts=RuleFacts.read(lc.getRuleFactsJson());
  assertThat(facts).containsEntry(Field.LC_PRESENTATION_DATE,"2026-10-14").containsEntry(Field.LC_TOLERANCE_PERCENT,"5");
 }
 @Test void anEnteredPresentationDateIsNeverReplaced(){
  var lc=new LetterOfCredit();lc.setRuleFactsJson(RuleFacts.encode(Map.of(Field.LC_PRESENTATION_DATE,"2026-10-01"),false));
  assertThat(PresentationDate.stamp(lc,LocalDate.of(2026,10,14))).isFalse();
  assertThat(RuleFacts.read(lc.getRuleFactsJson())).containsEntry(Field.LC_PRESENTATION_DATE,"2026-10-01");
 }
 @Test void aFileWithoutAnyFactsGetsTheDate(){
  var lc=new LetterOfCredit();
  assertThat(PresentationDate.stamp(lc,LocalDate.of(2026,10,14))).isTrue();
  assertThat(RuleFacts.read(lc.getRuleFactsJson())).containsOnlyKeys(Field.LC_PRESENTATION_DATE);
 }
}
