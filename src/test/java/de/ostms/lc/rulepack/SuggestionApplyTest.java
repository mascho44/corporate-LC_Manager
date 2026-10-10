package de.ostms.lc.rulepack;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.check.service.DocumentCheckService;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.document.repository.LcDocumentRepository;
import de.ostms.lc.document.service.SignatureEvidenceService;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static de.ostms.lc.rulepack.PackDefinition.Field;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:suggestionapply;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({RuleFactsController.class,DocumentFactSuggester.class,LcFactSuggester.class,SuggestionApplyTest.Beans.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SuggestionApplyTest {
 @TestConfiguration static class Beans{
  @Bean AuditService audit(){return mock(AuditService.class);}
  @Bean DocumentCheckService checks(){return mock(DocumentCheckService.class);}
  @Bean InternalPackService packs(){return mock(InternalPackService.class);}
  @Bean SignatureEvidenceService signatures(){var s=mock(SignatureEvidenceService.class);when(s.evidence(any())).thenReturn(SignatureEvidenceService.Evidence.unavailable());return s;}
 }
 @Autowired RuleFactsController controller;@Autowired LetterOfCreditRepository lcs;@Autowired LcDocumentRepository docs;
 private static UsernamePasswordAuthenticationToken auth(String... authorities){return new UsernamePasswordAuthenticationToken("synthetic-user","unused",Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());}

 private LetterOfCredit lc(String reference){
  var lc=new LetterOfCredit();lc.setReference(reference);
  lc.setApplicant("BUYER AG\nBAHNHOFSTR. 1\n8001 ZUERICH\nSWITZERLAND");lc.setBeneficiary("SUPPLIER CO. LTD\nNO. 88 JIANGUO ROAD\nSHANGHAI 200000\nP.R. CHINA");
  return lcs.saveAndFlush(lc);
 }
 private LcDocument packingList(LetterOfCredit lc){
  var d=new LcDocument();d.setLetterOfCredit(lc);d.setDocumentType(DocumentType.PACKING_LIST);d.setOriginalFilename("packing.pdf");d.setContentType("application/pdf");d.setContent(new byte[]{1});d.setFileSize(1);
  d.setExtractedText("PACKING LIST\nSeller: SUPPLIER CO. LTD\nNO. 88 JIANGUO ROAD\nSHANGHAI\nCHINA\nBuyer: BUYER AG\nBahnhofstr. 1\nZürich\nSchweiz\nTotal number of cartons: 48\nGross weight: 1,250.50 KGS\nNet weight: 1,100.00 KGS\n");
  return docs.saveAndFlush(d);
 }

 @Test void overviewListsLcAndDocumentProposalsWithTheirSources(){
  var lc=lc("SYN-OVERVIEW");packingList(lc);
  var overview=controller.allSuggestions(lc.getId());
  assertThat(overview.lc()).extracting(DocumentFactSuggester.Suggestion::field).contains(Field.LC_APPLICANT_ADDRESS_COUNTRY,Field.LC_BENEFICIARY_ADDRESS_COUNTRY,Field.LC_APPLICANT_ADDRESS);
  assertThat(overview.lc().stream().filter(s->s.field()==Field.LC_BENEFICIARY_ADDRESS_COUNTRY).findFirst().orElseThrow().value()).isEqualTo("China");
  var doc=overview.documents().get(0);assertThat(doc.filename()).isEqualTo("packing.pdf");
  var byField=new HashMap<Field,String>();doc.suggestions().forEach(s->byField.put(s.field(),s.value()));
  assertThat(byField).containsEntry(Field.DOCUMENT_PACKAGE_COUNT,"48").containsEntry(Field.DOCUMENT_GROSS_WEIGHT,"1250.50").containsEntry(Field.DOCUMENT_NET_WEIGHT,"1100.00").containsEntry(Field.DOCUMENT_WEIGHT_UNIT,"KG")
   .containsEntry(Field.DOCUMENT_BENEFICIARY_ADDRESS_COUNTRY,"China").containsEntry(Field.DOCUMENT_APPLICANT_ADDRESS_COUNTRY,"Switzerland");
  assertThat(overview.total()).isEqualTo(overview.lc().size()+doc.suggestions().size());
 }
 @Test void applyFillsOnlyEmptyFieldsAndNeverTouchesEnteredValues(){
  var lc=lc("SYN-APPLY");var doc=packingList(lc);
  controller.updateDocument(lc.getId(),doc.getId(),Map.of(Field.DOCUMENT_GROSS_WEIGHT,"999"),auth("PERM_DOCUMENT_UPLOAD"));
  var applied=controller.applySuggestions(lc.getId(),auth("PERM_DOCUMENT_UPLOAD","PERM_LC_EDIT"));
  assertThat(applied.documents()).isEqualTo(1);assertThat(applied.lcFields()).isEqualTo(3);assertThat(applied.documentFields()).isGreaterThanOrEqualTo(5);
  var facts=controller.document(lc.getId(),doc.getId());
  assertThat(facts).containsEntry(Field.DOCUMENT_GROSS_WEIGHT,"999").containsEntry(Field.DOCUMENT_PACKAGE_COUNT,"48").containsEntry(Field.DOCUMENT_NET_WEIGHT,"1100.00");
  assertThat(controller.lc(lc.getId())).containsEntry(Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"China").containsEntry(Field.LC_APPLICANT_ADDRESS_COUNTRY,"Switzerland");
  var again=controller.applySuggestions(lc.getId(),auth("PERM_DOCUMENT_UPLOAD","PERM_LC_EDIT"));
  assertThat(again.lcFields()+again.documentFields()).as("second run changes nothing").isZero();
 }
 @Test void withoutTheLcEditRightOnlyDocumentFactsAreApplied(){
  var lc=lc("SYN-NOEDIT");packingList(lc);
  var applied=controller.applySuggestions(lc.getId(),auth("PERM_DOCUMENT_UPLOAD"));
  assertThat(applied.lcFields()).isZero();assertThat(applied.documentFields()).isPositive();assertThat(controller.lc(lc.getId())).isEmpty();
 }
 @Test void aValueEnteredLaterIsKeptEvenIfTheProposalDiffers(){
  var lc=lc("SYN-KEEP");
  controller.updateLc(lc.getId(),Map.of(Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"Hong Kong"),auth("PERM_LC_EDIT"));
  controller.applySuggestions(lc.getId(),auth("PERM_DOCUMENT_UPLOAD","PERM_LC_EDIT"));
  assertThat(controller.lc(lc.getId())).containsEntry(Field.LC_BENEFICIARY_ADDRESS_COUNTRY,"Hong Kong");
  var suggestion=controller.allSuggestions(lc.getId()).lc().stream().filter(s->s.field()==Field.LC_BENEFICIARY_ADDRESS_COUNTRY).findFirst().orElseThrow();
  assertThat(suggestion.current()).isEqualTo("Hong Kong");
 }
}
