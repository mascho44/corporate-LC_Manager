package de.ostms.lc.rulepack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static de.ostms.lc.rulepack.PackDefinition.*;

class SupplementaryRuleFactsTest {
 @Test void newFactsHaveTypedLabelsAndRoundTripWithoutInventingUnknowns(){
  var values=Map.of(Field.DOCUMENT_PRESENTATION_DATE,"2026-10-08",
   Field.DOCUMENT_ON_BOARD_NOTATION_PRESENT,"true",Field.DOCUMENT_PACKAGE_COUNT,"12",
   Field.DOCUMENT_INCOTERM,"CIF",Field.DOCUMENT_INCOTERM_SOURCE,"page 1");
  assertThat(RuleFacts.read(RuleFacts.encode(values,true))).containsAllEntriesOf(values);
  assertThat(RuleFacts.read(RuleFacts.encode(Map.of(Field.DOCUMENT_SIGNED,""),true))).isEmpty();
  assertThat(RuleFacts.definitions(true)).allMatch(d->!d.label().equals(d.field().name()));
  assertThat(RuleFacts.definitions(false)).allMatch(d->!d.label().equals(d.field().name()));
  assertThat(Field.DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE.kind()).isEqualTo("BOOLEAN");
  assertThat(Field.DOCUMENT_PRESENTATION_DATE.kind()).isEqualTo("DATE");
  assertThat(Field.DOCUMENT_DRAFT_TENOR_DAYS.kind()).isEqualTo("NUMBER");
 }
 @Test void scopeAndInvalidTypedValuesRemainRejected(){
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_PRESENTATION_DATE,"2026-02-30"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_FRANCHISE_PRESENT,"yes"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.DOCUMENT_PACKAGE_COUNT,"1.5"),true)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RuleFacts.encode(Map.of(Field.LC_REQUIRED_ISSUER,"Demo"),false)).isInstanceOf(IllegalArgumentException.class);
  assertThat(RuleFacts.read(RuleFacts.encodeRequirements(Map.of(Field.LC_REQUIRED_ISSUER,"Demo")))).containsEntry(Field.LC_REQUIRED_ISSUER,"Demo");
 }
 @Test void shipmentToDocumentPresentationSupportsBoundedDays(){
  var codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
  for(var field:List.of(Field.DOCUMENT_SHIPMENT_DATE,Field.DOCUMENT_ON_BOARD_DATE)){
   var rule=new Rule("period-check","1.0.0",de.ostms.lc.document.domain.DocumentType.BILL_OF_LADING,field,Operator.WITHIN_DAYS,Field.DOCUMENT_PRESENTATION_DATE,Level.WARNING,"Period check","Internal synthetic test",Mode.AUTOMATIC,null,new Parameters(null,21,null,null,null,null));
   var tests=List.of(new TestCase("pass","period-check","2026-10-01","2026-10-22",Outcome.PASS),
    new TestCase("fail","period-check","2026-10-01","2026-10-23",Outcome.FAIL),
    new TestCase("unknown","period-check",null,"2026-10-22",Outcome.NOT_EVALUABLE));
   var pack=new PackDefinition(4,"synthetic-period","1.0.0","Synthetic period","OWN_INTERNAL","Internal","Synthetic test only",List.of(rule),tests);
   codec.validate(pack);assertThat(PackEvaluator.test(pack)).allMatch(PackEvaluator.TestResult::passed);
  }
 }
 @Test void documentAndLcPresentationDatesCanCompareToExpiry(){
  var codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
  for(var field:List.of(Field.DOCUMENT_PRESENTATION_DATE,Field.LC_PRESENTATION_DATE)){
   var rule=new Rule("date-check","1.0.0",de.ostms.lc.document.domain.DocumentType.BILL_OF_LADING,field,Operator.LTE,Field.LC_EXPIRY_DATE,Level.WARNING,"Date check","Internal synthetic test");
   var tests=List.of(new TestCase("pass","date-check","2026-10-08","2026-10-08",Outcome.PASS),
    new TestCase("fail","date-check","2026-10-09","2026-10-08",Outcome.FAIL),
    new TestCase("unknown","date-check",null,"2026-10-08",Outcome.NOT_EVALUABLE));
   var pack=new PackDefinition(4,"synthetic-dates","1.0.0","Synthetic dates","OWN_INTERNAL","Internal","Synthetic test only",List.of(rule),tests);
   codec.validate(pack);assertThat(PackEvaluator.test(pack)).allMatch(PackEvaluator.TestResult::passed);
  }
 }
}
