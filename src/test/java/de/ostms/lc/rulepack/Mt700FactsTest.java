package de.ostms.lc.rulepack;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class Mt700FactsTest {
 private static final String RAW=String.join("\n",
  ":40E:UCP LATEST VERSION",":39A:10/10",":42C:60 DAYS AFTER B/L DATE",":42A:DEUTDEFFXXX",":43P:NOT ALLOWED",":43T:ALLOWED",
  ":44A:HAMBURG",":44E:SHANGHAI PORT",":44F:ROTTERDAM",":44B:DUESSELDORF",
  ":45A:1000 PCS WIDGETS CIF ROTTERDAM INCOTERMS 2020",
  ":46A:+SIGNED COMMERCIAL INVOICE\n+FULL SET OF CLEAN SHIPPED ON BOARD BILL OF LADING MARKED FREIGHT PREPAID\n+INSURANCE POLICY FOR 110 PCT OF INVOICE VALUE COVERING INSTITUTE CARGO CLAUSES (A) AND INSTITUTE WAR CLAUSES",
  ":48:21 DAYS AFTER SHIPMENT DATE",":49:WITHOUT");
 private Map<Field,String> facts(String raw){var lc=new LetterOfCredit();lc.setRawMessage(raw);var m=new EnumMap<Field,String>(Field.class);Mt700Facts.detect(lc).forEach(f->m.put(f.field(),f.value()));return m;}
 @Test void readsStructuredTermsFromTheMessage(){
  var f=facts(RAW);
  assertThat(f).containsEntry(Field.LC_RULE_STANDARD,"UCP600").containsEntry(Field.LC_TOLERANCE_PERCENT,"10").containsEntry(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"true")
   .containsEntry(Field.LC_PARTIAL_SHIPMENT_ALLOWED,"false").containsEntry(Field.LC_TRANSSHIPMENT_ALLOWED,"true")
   .containsEntry(Field.LC_DRAFT_TENOR_DAYS,"60").containsEntry(Field.LC_DRAFT_TENOR_BASIS,"B/L DATE").containsEntry(Field.LC_DRAWEE,"DEUTDEFFXXX")
   .containsEntry(Field.LC_PLACE_OF_RECEIPT,"HAMBURG").containsEntry(Field.LC_LOADING_PORT,"SHANGHAI PORT").containsEntry(Field.LC_DISCHARGE_PORT,"ROTTERDAM").containsEntry(Field.LC_PLACE_OF_FINAL_DESTINATION,"DUESSELDORF")
   .containsEntry(Field.LC_PRESENTATION_PERIOD_DAYS,"21").containsEntry(Field.LC_INCOTERM,"CIF")
   .containsEntry(Field.LC_INSURANCE_MIN_PERCENT,"110").containsEntry(Field.LC_FREIGHT_PREPAID_REQUIRED,"true").containsEntry(Field.LC_FREIGHT_TERMS,"PREPAID").containsEntry(Field.LC_ON_BOARD_NOTATION_REQUIRED,"true");
  assertThat(f.get(Field.LC_INSURANCE_RISKS)).contains("INSTITUTE CARGO CLAUSES (A)").contains("INSTITUTE WAR CLAUSES");
  assertThat(f).doesNotContainKeys(Field.LC_DEPARTURE_AIRPORT,Field.LC_DESTINATION_AIRPORT);
 }
 @Test void sightDraftsAndUnequalTolerancesAreHandledCarefully(){
  var f=facts(":42C:AT SIGHT\n:39A:5/10\n:43P:ALLOWED");
  assertThat(f).containsEntry(Field.LC_DRAFT_TENOR_DAYS,"0").containsEntry(Field.LC_DRAFT_TENOR_BASIS,"SIGHT").containsEntry(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"true").doesNotContainKey(Field.LC_TOLERANCE_PERCENT);
 }
 @Test void airWaybillOnlyMakesTheAirportsFactsAndAmbiguousIncotermIsSkipped(){
  var f=facts(":44E:FRANKFURT\n:44F:SHANGHAI\n:45A:GOODS FOB OR CIF\n:46A:+AIR WAYBILL CONSIGNED TO APPLICANT");
  assertThat(f).containsEntry(Field.LC_DEPARTURE_AIRPORT,"FRANKFURT").containsEntry(Field.LC_DESTINATION_AIRPORT,"SHANGHAI").doesNotContainKey(Field.LC_INCOTERM);
 }
 @Test void exceedingNotAllowedSetsToleranceToFalse(){
  assertThat(facts(":39B:NOT EXCEEDING")).containsEntry(Field.LC_AMOUNT_TOLERANCE_ALLOWED,"false");
 }
}
