package de.ostms.lc.rulepack;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

class LcFactSuggesterTest {
 private final LcFactSuggester suggester=new LcFactSuggester();
 private static LetterOfCredit lc(String applicant,String beneficiary){var l=new LetterOfCredit();l.setApplicant(applicant);l.setBeneficiary(beneficiary);return l;}

 @Test void addressAndCountryComeFromTheSwiftPartyFields(){
  var found=suggester.suggest(lc("BUYER AG\nBAHNHOFSTR. 1\n8001 ZUERICH\nSWITZERLAND","SUPPLIER CO. LTD\nNO. 88 JIANGUO ROAD\nSHANGHAI 200000\nP.R. CHINA")).stream().collect(Collectors.toMap(DocumentFactSuggester.Suggestion::field,DocumentFactSuggester.Suggestion::value));
  assertThat(found).containsEntry(Field.LC_APPLICANT_ADDRESS,"BAHNHOFSTR. 1, 8001 ZUERICH").containsEntry(Field.LC_APPLICANT_ADDRESS_COUNTRY,"Switzerland").containsEntry(Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"China");
 }
 @Test void nothingIsProposedWithoutAnAddressOrCountry(){
  assertThat(suggester.suggest(lc(null,null))).isEmpty();
  var found=suggester.suggest(lc("ONLY A NAME","NAME\nSTREET 1\nCITY"));
  assertThat(found).extracting(DocumentFactSuggester.Suggestion::field).doesNotContain(Field.LC_BENEFICIARY_ADDRESS_COUNTRY,Field.LC_APPLICANT_ADDRESS_COUNTRY);
 }
 @Test void alreadyStoredIdenticalValuesAreNotProposedAgainAndChangedOnesShowTheCurrentValue(){
  var l=lc("BUYER AG\nSTR 1\nSWITZERLAND","SUPPLIER\nROAD\nCHINA");
  l.setRuleFactsJson(RuleFacts.encode(java.util.Map.of(Field.LC_APPLICANT_ADDRESS_COUNTRY,"Switzerland",Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"Hong Kong"),false));
  var found=suggester.suggest(l);
  assertThat(found).extracting(DocumentFactSuggester.Suggestion::field).doesNotContain(Field.LC_APPLICANT_ADDRESS_COUNTRY);
  assertThat(found.stream().filter(s->s.field()==Field.LC_BENEFICIARY_ADDRESS_COUNTRY).findFirst().orElseThrow().current()).isEqualTo("Hong Kong");
 }
}
