package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentIndicatorsTest {
 private Set<Field> fields(DocumentType t,String text){var s=new HashSet<Field>();DocumentIndicators.detect(t,text).forEach(h->s.add(h.field()));return s;}
 @Test void transportPhrasesAreOnlyEvaluatedOnTransportDocuments(){
  String text="SHIPPED ON BOARD. Transhipment permitted. Container FCL. Signed as agent for the carrier.";
  assertThat(fields(DocumentType.BILL_OF_LADING,text)).contains(Field.DOCUMENT_SHIPPED_ON_BOARD_INDICATED,Field.DOCUMENT_TRANSSHIPMENT_INDICATED,Field.DOCUMENT_CONTAINERISED_SHIPMENT_INDICATED,Field.DOCUMENT_AGENT_PRINCIPAL_INDICATED,Field.DOCUMENT_SIGNED_BY_AGENT_INDICATED);
  assertThat(fields(DocumentType.COMMERCIAL_INVOICE,text)).isEmpty();
 }
 @Test void insurancePhrasesAndGenericOnesWork(){
  assertThat(fields(DocumentType.INSURANCE_CERTIFICATE,"Premium paid. Claims payable in Germany. Irrespective of percentage. Deductible 1%. Endorsed in blank. Provisional cover."))
   .contains(Field.DOCUMENT_PREMIUM_PAID_INDICATED,Field.DOCUMENT_CLAIM_EXPIRY_INDICATED,Field.DOCUMENT_IRRESPECTIVE_OF_PERCENTAGE,Field.DOCUMENT_FRANCHISE_PRESENT,Field.DOCUMENT_ENDORSEMENT_PRESENT,Field.DOCUMENT_PROVISIONAL_INDICATED);
 }
 @Test void noTextMeansNoHits(){assertThat(DocumentIndicators.detect(DocumentType.BILL_OF_LADING,null)).isEmpty();assertThat(fields(DocumentType.BILL_OF_LADING,"nothing relevant")).isEmpty();}
}
