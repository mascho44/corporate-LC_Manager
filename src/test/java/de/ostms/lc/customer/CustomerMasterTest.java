package de.ostms.lc.customer;

import de.ostms.lc.document.domain.DocumentInboxItem;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:customermaster;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
class CustomerMasterTest {
 @Autowired CustomerRepository customers;@Autowired InboxCustomerAssignmentRepository assignments;@Autowired DocumentInboxRepository inbox;@Autowired TenantRepository tenants;@Autowired de.ostms.lc.lc.repository.LetterOfCreditRepository lettersOfCredit;@Autowired de.ostms.lc.capture.InboxCaptureInfoRepository captureInfos;
 CustomerService service;InboxCustomerService inboxCustomers;

 @BeforeEach void setUp(){
  if(tenants.findById(Tenant.DEFAULT_ID).isEmpty())tenants.saveAndFlush(new Tenant());
  service=new CustomerService(customers);inboxCustomers=new InboxCustomerService(inbox,customers,assignments,lettersOfCredit,captureInfos);
 }
 DocumentInboxItem item(String filename,String text){var i=new DocumentInboxItem();i.setOriginalFilename(filename);i.setContentType("application/pdf");i.setFileSize(10);i.setReceivedBy("capture:Scanner");i.setExtractionStatus("EXTRACTED");i.setExtractedText(text);return inbox.saveAndFlush(i);}

 @Test void customersAreValidatedAndNormalised(){
  var view=service.create(new CustomerService.Request("  Beispiel Export GmbH ","4711","0012","de89 3704 0044 0532 0130 00","cobadeffxxx"),"admin");
  assertThat(view.iban()).isEqualTo("DE89370400440532013000");assertThat(view.bic()).isEqualTo("COBADEFFXXX");assertThat(view.name()).isEqualTo("Beispiel Export GmbH");assertThat(view.source()).isEqualTo("MANUAL");
  assertThatThrownBy(()->service.create(new CustomerService.Request("Zwei","4711",null,null,null),"admin")).hasMessageContaining("Kundennummer ist schon vergeben");
  assertThatThrownBy(()->service.create(new CustomerService.Request("Drei",null,null,"DE00370400440532013000",null),"admin")).hasMessageContaining("IBAN ist ungültig");
  assertThatThrownBy(()->service.create(new CustomerService.Request("Vier",null,null,null,"XYZ"),"admin")).hasMessageContaining("BIC");
  assertThatThrownBy(()->service.create(new CustomerService.Request(" ",null,null,null,null),"admin")).hasMessageContaining("Namen");
  var changed=service.update(view.id(),new CustomerService.Request("Beispiel Export AG","4711",null,null,null),"admin");
  assertThat(changed.name()).isEqualTo("Beispiel Export AG");assertThat(service.list()).hasSize(1);
  service.delete(view.id());assertThat(service.list()).isEmpty();
 }

 @Test void submissionLettersGetASuggestionAndTheChosenCustomerIsKept(){
  var customer=service.create(new CustomerService.Request("Beispiel Export GmbH","4711-08",null,"DE89370400440532013000",null),"admin");
  var other=service.create(new CustomerService.Request("Andere GmbH","1",null,null,null),"admin");
  var letter=item("brief.pdf",SubmissionLetterTest.LETTER);
  var invoice=item("rechnung.pdf","Rechnung Nr. 5\nBetrag 100,00 EUR");
  var entries=inboxCustomers.overview();
  assertThat(entries).containsOnlyKeys(letter.getId());
  var entry=entries.get(letter.getId());
  assertThat(entry.clear()).isTrue();assertThat(entry.candidates().get(0).customerId()).isEqualTo(customer.id());assertThat(entry.assigned()).isNull();
  assertThat(entry.fields().iban()).isEqualTo("DE89370400440532013000");

  var confirmed=inboxCustomers.assign(letter.getId(),customer.id(),"anna");
  assertThat(confirmed.method()).isEqualTo("SUGGESTION");
  assertThat(inboxCustomers.overview().get(letter.getId()).assigned().name()).isEqualTo("Beispiel Export GmbH");
  var manual=inboxCustomers.assign(letter.getId(),other.id(),"anna");
  assertThat(manual.method()).isEqualTo("MANUAL");
  assertThat(assignments.findAll()).hasSize(1);
  inboxCustomers.unassign(letter.getId());
  assertThat(inboxCustomers.overview().get(letter.getId()).assigned()).isNull();
  var invoiceAssigned=inboxCustomers.assign(invoice.getId(),customer.id(),"anna");
  assertThat(invoiceAssigned.method()).isEqualTo("MANUAL");
  assertThat(inboxCustomers.overview()).containsKey(invoice.getId());
 }

 @Test void withoutCustomerDataAPersonChoosesTheCustomer(){
  var customer=service.create(new CustomerService.Request("Beispiel Export GmbH","4711",null,null,null),"admin");
  var letter=item("einreichung.pdf","Einreichung der Dokumente\nKunde: Unbekannt\nBitte um Bearbeitung.");
  var entry=inboxCustomers.overview().get(letter.getId());
  assertThat(entry.candidates()).isEmpty();assertThat(entry.clear()).isFalse();
  assertThat(inboxCustomers.assign(letter.getId(),customer.id(),"anna").method()).isEqualTo("MANUAL");
 }

 de.ostms.lc.lc.domain.LetterOfCredit lc(String reference,String beneficiary,de.ostms.lc.lc.domain.LetterOfCreditStatus status){
  var l=new de.ostms.lc.lc.domain.LetterOfCredit();l.setReference(reference);l.setBeneficiary(beneficiary);l.setStatus(status);return lettersOfCredit.saveAndFlush(l);
 }

 @Test void theCustomerSteersTheChoiceOfTheLcFileAndTheLcNamedByTheLetterComesFirst(){
  var customer=service.create(new CustomerService.Request("Beispiel Export GmbH",null,null,"DE89370400440532013000",null),"admin");
  lc("LCB-ALPHA","BEISPIEL EXPORT GMBH, STUTTGART",de.ostms.lc.lc.domain.LetterOfCreditStatus.ACTIVE);
  lc("LCB0319990000001","ANDERER BEGÜNSTIGTER",de.ostms.lc.lc.domain.LetterOfCreditStatus.ACTIVE);
  lc("LCB-ALT","BEISPIEL EXPORT GMBH",de.ostms.lc.lc.domain.LetterOfCreditStatus.CLOSED);
  lc("LCB-FREMD","FREMDE FIRMA AG",de.ostms.lc.lc.domain.LetterOfCreditStatus.ACTIVE);
  var letter=item("einreichung.pdf",SubmissionLetterTest.BANK_FORM);
  var entry=inboxCustomers.overview().get(letter.getId());
  assertThat(entry.clear()).isTrue();assertThat(entry.via()).isEqualTo("LETTER");
  assertThat(entry.lcs()).extracting(InboxCustomerService.LcOption::reference).containsExactly("LCB0319990000001","LCB-ALPHA");
  assertThat(entry.lcs().get(0).byLetter()).isTrue();assertThat(entry.lcs().get(1).byLetter()).isFalse();
  inboxCustomers.assign(letter.getId(),customer.id(),"anna");
  assertThat(inboxCustomers.overview().get(letter.getId()).lcs()).extracting(InboxCustomerService.LcOption::reference).containsExactly("LCB0319990000001","LCB-ALPHA");
 }

 @Test void withoutAClearCustomerNoLcFilesAreSuggested(){
  service.create(new CustomerService.Request("Beispiel Export GmbH",null,null,null,null),"admin");
  lc("LCB-ALPHA","BEISPIEL EXPORT GMBH",de.ostms.lc.lc.domain.LetterOfCreditStatus.ACTIVE);
  var letter=item("einreichung.pdf","Dokumenteneinreichung\nKunde: Beispiel Export GmbH\nKundennummer: 99\nIBAN: DE89 3704 0044 0532 0130 00");
  var entry=inboxCustomers.overview().get(letter.getId());
  assertThat(entry.clear()).isFalse();assertThat(entry.lcs()).isEmpty();
 }

 @Test void otherDocumentsOfTheSameBatchGetTheCustomerOfTheConfirmedLetter(){
  var customer=service.create(new CustomerService.Request("Beispiel Export GmbH",null,null,"DE89370400440532013000",null),"admin");
  lc("LCB-ALPHA","BEISPIEL EXPORT GMBH",de.ostms.lc.lc.domain.LetterOfCreditStatus.ACTIVE);
  var letter=item("einreichung.pdf",SubmissionLetterTest.BANK_FORM);
  var invoice=item("rechnung.pdf","Rechnung Nr. 5\nBetrag 100,00 EUR");
  var packing=item("packliste.pdf","Packing list 1");
  var otherBatch=item("fremd.pdf","Rechnung Nr. 9");
  captureInfos.saveAndFlush(new de.ostms.lc.capture.InboxCaptureInfo(letter.getId(),"capture:x","B1","S1",2,2,null,"a1"));
  captureInfos.saveAndFlush(new de.ostms.lc.capture.InboxCaptureInfo(invoice.getId(),"capture:x","B1","S1",1,1,null,"a2"));
  captureInfos.saveAndFlush(new de.ostms.lc.capture.InboxCaptureInfo(packing.getId(),"capture:x","B1","S1",1,1,null,"a3"));
  captureInfos.saveAndFlush(new de.ostms.lc.capture.InboxCaptureInfo(otherBatch.getId(),"capture:x","B2","S1",1,1,null,"a4"));
  assertThat(inboxCustomers.overview()).containsOnlyKeys(letter.getId());
  inboxCustomers.assign(letter.getId(),customer.id(),"anna");
  var entries=inboxCustomers.overview();
  assertThat(entries).containsOnlyKeys(letter.getId(),invoice.getId(),packing.getId());
  var inherited=entries.get(invoice.getId());
  assertThat(inherited.via()).isEqualTo("BATCH");assertThat(inherited.assigned()).isNull();assertThat(inherited.clear()).isTrue();
  assertThat(inherited.candidates().get(0).customerId()).isEqualTo(customer.id());assertThat(inherited.candidates().get(0).reason()).isEqualTo("Stapel B1");
  assertThat(inherited.lcs()).extracting(InboxCustomerService.LcOption::reference).contains("LCB-ALPHA");
 }
}
