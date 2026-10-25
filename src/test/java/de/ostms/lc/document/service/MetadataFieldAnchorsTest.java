package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import de.ostms.lc.document.api.DocumentInboxAttachRequest.Metadata;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
class MetadataFieldAnchorsTest {
 @Test void unseenInvoiceUsesNewValuesNotTrainingValues(){
  var confirmed=new DocumentMetadataTraining.Confirmation(new Metadata("LC123","INV123",new BigDecimal("100.25"),"EUR"),LocalDate.of(2026,8,25));
  var pattern=MetadataFieldAnchors.learn("Example Exporter Invoice\nCredit ref: LC123\nBill number: INV123\nBilling date: 25.08.2026\nPayable: 100.25\nCurrency: EUR",confirmed);
  var proposal=MetadataFieldAnchors.apply("Example Exporter Invoice\nCredit ref: LC999\nBill number: INV999\nBilling date: 26.09.2026\nPayable: 999.50\nCurrency: USD",pattern);
  assertThat(proposal.values().metadata().reference()).isEqualTo("LC999");assertThat(proposal.values().metadata().documentNumber()).isEqualTo("INV999");assertThat(proposal.values().metadata().amount()).isEqualByComparingTo("999.50");assertThat(proposal.values().metadata().currency()).isEqualTo("USD");assertThat(proposal.values().documentDate()).isEqualTo(LocalDate.of(2026,9,26));assertThat(proposal.evidence().get("amount")).contains("999.50");
 }
 @Test void unrelatedHeadingAndConflictingValuesRemainOpen(){
  var pattern=MetadataFieldAnchors.learn("Exporter Invoice\nCredit ref: LC123\nBill number: INV123",new DocumentMetadataTraining.Confirmation(new Metadata("LC123","INV123",null,null),null));
  assertThat(MetadataFieldAnchors.apply("Other Exporter Invoice\nCredit ref: LC999\nBill number: INV999",pattern)).isNull();
  assertThat(MetadataFieldAnchors.apply("Exporter Invoice\nCredit ref: LC999\nCredit ref: LC888\nBill number: INV999",pattern)).isNull();
 }
 @Test void labelOnPreviousLineIsSupportedAndAmbiguousDateIsNotGuessed(){
  var pattern=MetadataFieldAnchors.learn("Exporter Invoice\nBill number:\nINV123\nCredit ref: LC123\nBilling date: 25.08.2026",new DocumentMetadataTraining.Confirmation(new Metadata("LC123","INV123",null,null),LocalDate.of(2026,8,25)));
  var result=MetadataFieldAnchors.apply("Exporter Invoice\nBill number:\nINV999\nCredit ref: LC999\nBilling date: 05/08/2026",pattern);
  assertThat(result.values().documentDate()).isNull();assertThat(result.evidence()).doesNotContainKey("documentDate");
 }
}
