package de.ostms.lc.rulepack;
import de.ostms.lc.document.domain.DocumentType;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextFactsTest {
 private Map<Field,String> doc(DocumentType t,String text){var m=new EnumMap<Field,String>(Field.class);DocumentTextFacts.detect(t,text).forEach(f->m.put(f.field(),f.value()));return m;}
 @Test void invoiceQuantityPriceNumberAndShipmentDate(){
  var f=doc(DocumentType.COMMERCIAL_INVOICE,"COMMERCIAL INVOICE\nInvoice No: INV-2026/001\nQuantity: 1,000 PCS\nUnit price: USD 12.50\nDate of shipment: 12.03.2026\n");
  assertThat(f).containsEntry(Field.DOCUMENT_NUMBER,"INV-2026/001").containsEntry(Field.DOCUMENT_QUANTITY,"1000").containsEntry(Field.DOCUMENT_QUANTITY_UNIT,"PCS").containsEntry(Field.DOCUMENT_UNIT_PRICE_AMOUNT,"12.50").containsEntry(Field.DOCUMENT_SHIPMENT_DATE,"2026-03-12").doesNotContainKey(Field.DOCUMENT_INVOICE_REFERENCE);
 }
 @Test void ambiguousValuesAreNotProposed(){
  assertThat(doc(DocumentType.COMMERCIAL_INVOICE,"Quantity: 10 PCS\nQuantity: 20 PCS")).doesNotContainKeys(Field.DOCUMENT_QUANTITY,Field.DOCUMENT_QUANTITY_UNIT);
 }
 @Test void transportPartiesAndInvoiceReference(){
  var f=doc(DocumentType.BILL_OF_LADING,"BILL OF LADING\nConsignee: ACME Trading GmbH\nNotify Party: Global Logistics Ltd\nInvoice No: INV-77\n");
  assertThat(f).containsEntry(Field.DOCUMENT_CONSIGNEE,"ACME Trading GmbH").containsEntry(Field.DOCUMENT_NOTIFY_PARTY,"Global Logistics Ltd").containsEntry(Field.DOCUMENT_INVOICE_REFERENCE,"INV-77");
 }
 @Test void insuranceAndDraftTerms(){
  var i=doc(DocumentType.INSURANCE_CERTIFICATE,"INSURANCE CERTIFICATE\nInsured: ACME GmbH\nEffective date: 01.03.2026\nCoverage from 01.03.2026 to 30.04.2026\n");
  assertThat(i).containsEntry(Field.DOCUMENT_INSURED_PARTY,"ACME GmbH").containsEntry(Field.DOCUMENT_INSURANCE_EFFECTIVE_DATE,"2026-03-01").containsEntry(Field.DOCUMENT_COVERAGE_FROM,"2026-03-01").containsEntry(Field.DOCUMENT_COVERAGE_TO,"2026-04-30").containsEntry(Field.DOCUMENT_INSURANCE_TYPE,"CERTIFICATE");
  var d=doc(DocumentType.BILL_OF_EXCHANGE,"BILL OF EXCHANGE\nPay at 60 days after B/L date\nDrawn on: Deutsche Bank AG\n");
  assertThat(d).containsEntry(Field.DOCUMENT_DRAFT_TENOR_DAYS,"60").containsEntry(Field.DOCUMENT_DRAFT_TENOR_BASIS,"B/L DATE").containsEntry(Field.DOCUMENT_DRAWEE,"Deutsche Bank AG");
  assertThat(doc(DocumentType.BILL_OF_EXCHANGE,"Payable at sight")).containsEntry(Field.DOCUMENT_DRAFT_TENOR_DAYS,"0");
 }
 @Test void lcSideReadsQuantityAndRequirementsFrom45aAnd46a(){
  var lc=new LetterOfCredit();lc.setRawMessage(":45A:1000 PCS WIDGETS\n:46A:+INSURANCE POLICY, PREMIUM PAID, ENDORSED IN BLANK\n+CERTIFICATE OF ORIGIN FORM A\n+B/L CONSIGNED TO ORDER, NOTIFY APPLICANT");
  var m=new EnumMap<Field,String>(Field.class);Mt700Facts.detect(lc).forEach(f->m.put(f.field(),f.value()));
  assertThat(m).containsEntry(Field.LC_QUANTITY,"1000").containsEntry(Field.LC_QUANTITY_UNIT,"PCS").containsEntry(Field.LC_INSURANCE_TYPE,"POLICY").containsEntry(Field.LC_PREMIUM_PAID_REQUIRED,"true").containsEntry(Field.LC_ENDORSEMENT_REQUIRED,"true").containsEntry(Field.LC_REQUIRED_FORM_TYPE,"FORM A").containsEntry(Field.LC_CONSIGNEE,"TO ORDER").containsEntry(Field.LC_NOTIFY_PARTY,"APPLICANT");
 }
}
