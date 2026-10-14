package de.ostms.lc.rulepack;

import de.ostms.lc.lc.domain.LetterOfCredit;

import java.time.LocalDate;
import java.util.EnumMap;
import static de.ostms.lc.rulepack.PackDefinition.Field;

/** The date the documents were presented ("Dokumente eingereicht") is proposed as today when the file reaches that status; an entered value is never replaced. */
public final class PresentationDate {
 private PresentationDate(){}
 /** Sets the presentation date fact of the LC to `today` unless one is already there; returns whether it was set. */
 public static boolean stamp(LetterOfCredit lc,LocalDate today){
  var facts=new EnumMap<Field,String>(Field.class);facts.putAll(RuleFacts.read(lc.getRuleFactsJson()));
  String existing=facts.get(Field.LC_PRESENTATION_DATE);
  if(existing!=null&&!existing.isBlank())return false;
  facts.put(Field.LC_PRESENTATION_DATE,today.toString());
  lc.setRuleFactsJson(RuleFacts.encode(facts,false));return true;
 }
}
