package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.lc.domain.LetterOfCredit;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InternalPackServiceTest {
 final PackCodec codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
 final PackVersionRepository versions=mock(PackVersionRepository.class);
 final PackSelectionRepository selections=mock(PackSelectionRepository.class);
 final AuditService audit=mock(AuditService.class);
 final InternalPackService service=new InternalPackService(codec,versions,selections,audit);
 final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("synthetic-admin","unused");
 StoredPackVersion version()throws Exception{
  var p=codec.parse(PackCodecTest.example());var v=new StoredPackVersion();
  v.packId=p.packId();v.version=p.version();v.definitionJson=codec.canonical(p);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));return v;
 }
 @Test void missingInputsUseActivePacksAndRemoveAlreadyCapturedFacts()throws Exception{
  byte[] source;try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v2.json")){source=input.readAllBytes();}
  var pack=codec.parse(source);var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
  var lc=new LetterOfCredit();var doc=new LcDocument();doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
  assertThat(service.missingDocumentFields(lc,doc)).contains(PackDefinition.Field.DOCUMENT_ISSUER);
  doc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_ISSUER,"Synthetic issuer"),true));
  assertThat(service.missingDocumentFields(lc,doc)).doesNotContain(PackDefinition.Field.DOCUMENT_ISSUER);
  selected.activeVersionId=null;assertThat(service.missingDocumentFields(lc,doc)).isEmpty();
 }
 @Test void deletionRejectsActiveVersion()throws Exception{
  var v=version();var selection=new PackSelection();selection.activeVersionId=v.id;
  when(selections.locked(v.packId)).thenReturn(Optional.of(selection));
  assertThatThrownBy(()->service.delete(v.id,auth)).isInstanceOf(IllegalArgumentException.class);
  verify(versions,never()).delete(any(StoredPackVersion.class));verifyNoInteractions(audit);
 }
 @Test void deletionClearsPreviousPointerAndIsAudited()throws Exception{
  var v=version();var selection=new PackSelection();selection.previousVersionId=v.id;
  when(selections.locked(v.packId)).thenReturn(Optional.of(selection));
  service.delete(v.id,auth);assertThat(selection.previousVersionId).isNull();
  var order=inOrder(selections,versions);order.verify(selections).locked(v.packId);order.verify(selections).flush();order.verify(versions).delete(v);order.verify(versions).flush();
  verify(audit).recordInTransaction(eq(auth),eq("LC_RULE_PACK_DELETED"),eq("RULE_PACK_VERSION"),eq(v.id),anyString());
 }
 @Test void importedVersionIsInactiveImmutableAndAudited()throws Exception{
  when(selections.existsById(anyString())).thenReturn(true);
  var v=service.importPack(PackCodecTest.example(),auth);
  assertThat(v.testsPassed).isTrue();verify(selections,never()).locked(anyString());
  verify(audit).recordInTransaction(eq(auth),eq("LC_RULE_PACK_IMPORTED"),anyString(),eq(v.id),anyString());
  when(versions.existsByPackIdAndVersion(v.packId,v.version)).thenReturn(true);
  assertThatThrownBy(()->service.importPack(PackCodecTest.example(),auth)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void activationNeedsRightsAndPassingTestsAndPinsPreviousVersion()throws Exception{
  var v=version();var selection=new PackSelection();selection.id=v.packId;selection.activeVersionId=UUID.randomUUID();
  var previous=selection.activeVersionId;when(selections.locked(v.packId)).thenReturn(Optional.of(selection));
  assertThatThrownBy(()->service.activate(v.id,false,auth)).isInstanceOf(IllegalArgumentException.class);
  v.testsPassed=false;assertThatThrownBy(()->service.activate(v.id,true,auth)).isInstanceOf(IllegalArgumentException.class);
  v.testsPassed=true;service.activate(v.id,true,auth);assertThat(selection.activeVersionId).isEqualTo(v.id);assertThat(selection.previousVersionId).isEqualTo(previous);
  service.deactivate(v.packId,auth);assertThat(selection.activeVersionId).isNull();assertThat(selection.previousVersionId).isEqualTo(v.id);
 }
 @Test void inconsistentStoredTestFlagCannotActivateFailingTests()throws Exception{
  var v=version();
  v.definitionJson=v.definitionJson.replace("\"left\":\"101\"","\"left\":\"99\"");v.checksum=codec.digest(v.definitionJson);
  assertThatThrownBy(()->service.activate(v.id,true,auth)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void activePackProducesVersionedReviewableFindingsAndMissingFactsWarn()throws Exception{
  var v=version();var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
  var lc=new LetterOfCredit();lc.setAmount(new BigDecimal("100"));
  var doc=new LcDocument();doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);doc.setOriginalFilename("synthetic.pdf");doc.setAmount(new BigDecimal("101"));
  var finding=service.evaluate(lc,List.of(doc)).get(0);
  assertThat(finding.severity().name()).isEqualTo("WARNING");assertThat(finding.rule().version()).isEqualTo("1.0.0/1.0.0");
  assertThat(finding.withInputFingerprint("test").rule()).isEqualTo(finding.rule());
  var v2=version();v2.version="2.0.0";v2.definitionJson=v2.definitionJson.replace("\"version\":\"1.0.0\",\"name\"","\"version\":\"2.0.0\",\"name\"");v2.checksum=codec.digest(v2.definitionJson);
  selected.activeVersionId=v2.id;var changed=service.evaluate(lc,List.of(doc)).get(0);
  assertThat(changed.reviewFingerprint()).isNotEqualTo(finding.reviewFingerprint());
  doc.setAmount(null);assertThat(service.evaluate(lc,List.of(doc)).get(0).message()).contains("nicht prüfbar");
  assertThat(service.evaluate(lc,List.of()).get(0).message()).contains("Dokument fehlt");
 }
 @Test void schemaThreeUsesUniquePeersAndTracksTheirContent()throws Exception{
  byte[] source;try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v3.json")){source=input.readAllBytes();}
  var pack=codec.parse(source);var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
  var lc=new LetterOfCredit();lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_INSURANCE_MIN_PERCENT,"125"),false));
  var insurance=new LcDocument();insurance.setDocumentType(DocumentType.INSURANCE_CERTIFICATE);insurance.setOriginalFilename("insurance.pdf");
  insurance.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_INSURED_AMOUNT,"1250",PackDefinition.Field.DOCUMENT_INSURANCE_CURRENCY,"EUR"),true));
  var invoice=new LcDocument();invoice.setDocumentType(DocumentType.COMMERCIAL_INVOICE);invoice.setOriginalFilename("invoice.pdf");invoice.setAmount(new BigDecimal("1000"));invoice.setCurrency("EUR");invoice.setContent(new byte[]{1});
  var first=service.evaluate(lc,List.of(insurance,invoice)).get(0);assertThat(first.severity().name()).isEqualTo("OK");
  invoice.setContent(new byte[]{2});assertThat(service.evaluate(lc,List.of(insurance,invoice)).get(0).reviewFingerprint()).isNotEqualTo(first.reviewFingerprint());
  var duplicate=new LcDocument();duplicate.setDocumentType(DocumentType.COMMERCIAL_INVOICE);duplicate.setAmount(new BigDecimal("1000"));duplicate.setCurrency("EUR");
  assertThat(service.evaluate(lc,List.of(insurance,invoice,duplicate)).get(0).message()).contains("nicht prüfbar");
  var insuranceFacts=new EnumMap<PackDefinition.Field,String>(PackDefinition.Field.class);insuranceFacts.putAll(RuleFacts.read(insurance.getRuleFactsJson()));insuranceFacts.put(PackDefinition.Field.DOCUMENT_PRESENTATION_GROUP,"presentation-1");insurance.setRuleFactsJson(RuleFacts.encode(insuranceFacts,true));
  invoice.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_PRESENTATION_GROUP,"presentation-1"),true));
  assertThat(service.evaluate(lc,List.of(insurance,invoice,duplicate)).get(0).severity().name()).isEqualTo("OK");
  invoice.setCurrency("USD");assertThat(service.evaluate(lc,List.of(insurance,invoice)).get(0).message()).contains("nicht prüfbar");
 }
 @Test void schemaFourDerivesBasisAndChecksScopeBeforeMissingDocuments()throws Exception{
  byte[] source;try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v4.json")){source=input.readAllBytes();}
  var demo=codec.parse(source);var base=demo.rules().get(0);
  var rule=new PackDefinition.Rule(base.id(),base.version(),base.documentType(),base.left(),base.operator(),base.right(),base.severity(),base.message(),base.sourceReference(),base.mode(),List.of(new PackDefinition.Condition(PackDefinition.Field.LC_RULE_STANDARD,PackDefinition.Operator.EQ,"UCP600")),base.parameters());
  var tests=new ArrayList<PackDefinition.TestCase>();for(var test:demo.tests().stream().filter(t->t.ruleId().equals(rule.id())).toList()){
   var facts=new EnumMap<PackDefinition.Field,String>(PackDefinition.Field.class);facts.putAll(test.facts());facts.put(PackDefinition.Field.LC_RULE_STANDARD,"UCP600");tests.add(new PackDefinition.TestCase(test.name(),test.ruleId(),test.left(),test.right(),test.expected(),facts));
  }
  tests.add(new PackDefinition.TestCase("excluded",rule.id(),"1250","1000",PackDefinition.Outcome.NOT_APPLICABLE,Map.of(PackDefinition.Field.LC_RULE_STANDARD,"OTHER")));
  tests.add(new PackDefinition.TestCase("unknown scope",rule.id(),"1250","1000",PackDefinition.Outcome.NOT_EVALUABLE,Map.of()));
  var pack=new PackDefinition(4,demo.packId(),demo.version(),demo.name(),demo.origin(),demo.license(),demo.rightsStatement(),List.of(rule),tests);codec.validate(pack);
  var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));var selection=new PackSelection();selection.id=v.packId;selection.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selection));
  var lc=new LetterOfCredit();lc.setCurrency("EUR");
  lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_RULE_STANDARD,"UCP600",PackDefinition.Field.LC_CLAIMED_AMOUNT,"1000",PackDefinition.Field.LC_GROSS_GOODS_AMOUNT,"1200"),false));
  var doc=new LcDocument();doc.setDocumentType(DocumentType.INSURANCE_CERTIFICATE);doc.setOriginalFilename("synthetic-cover.pdf");doc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_INSURED_AMOUNT,"1500",PackDefinition.Field.DOCUMENT_INSURANCE_CURRENCY,"EUR"),true));
  var first=service.evaluate(lc,List.of(doc)).get(0);assertThat(first.severity().name()).isEqualTo("OK");assertThat(first.documentEvidence()).contains("1200");
  lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_RULE_STANDARD,"UCP600",PackDefinition.Field.LC_CLAIMED_AMOUNT,"1000"),false));assertThat(service.evaluate(lc,List.of(doc)).get(0).message()).contains("nicht prüfbar");
  lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_RULE_STANDARD,"OTHER"),false));assertThat(service.evaluate(lc,List.of()).get(0).message()).contains("nicht anwendbar").doesNotContain("fehlt");
 }
 @Test void supplementaryPeerFactsUseCapturedNumbersAndUniqueDocuments()throws Exception{
  for(var pair:List.of(
   List.of(PackDefinition.Field.DOCUMENT_INVOICE_REFERENCE,PackDefinition.Field.PEER_DOCUMENT_NUMBER),
   List.of(PackDefinition.Field.DOCUMENT_PACKAGE_COUNT,PackDefinition.Field.PEER_PACKAGE_COUNT),
   List.of(PackDefinition.Field.DOCUMENT_SHIPPING_MARKS,PackDefinition.Field.PEER_SHIPPING_MARKS))){
   String value=pair.get(0)==PackDefinition.Field.DOCUMENT_PACKAGE_COUNT?"12":"SYNTHETIC-123";
   var rule=new PackDefinition.Rule("peer-check","1.0.0",DocumentType.CERTIFICATE_OF_ORIGIN,pair.get(0),PackDefinition.Operator.EQ,pair.get(1),PackDefinition.Level.WARNING,"Synthetic peer","Internal",PackDefinition.Mode.AUTOMATIC,null,new PackDefinition.Parameters(DocumentType.COMMERCIAL_INVOICE,null,null,null,null,null));
   var tests=List.of(new PackDefinition.TestCase("pass","peer-check",value,value,PackDefinition.Outcome.PASS),
    new PackDefinition.TestCase("fail","peer-check",value,"13",PackDefinition.Outcome.FAIL),
    new PackDefinition.TestCase("unknown","peer-check",null,value,PackDefinition.Outcome.NOT_EVALUABLE));
   var pack=new PackDefinition(4,"synthetic-peer","1.0.0","Synthetic peer","OWN_INTERNAL","Internal","Synthetic only",List.of(rule),tests);codec.validate(pack);
   var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
   when(versions.findById(v.id)).thenReturn(Optional.of(v));var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
   var doc=new LcDocument();doc.setDocumentType(DocumentType.CERTIFICATE_OF_ORIGIN);doc.setOriginalFilename("synthetic-certificate.pdf");doc.setRuleFactsJson(RuleFacts.encode(Map.of(pair.get(0),value),true));
   var peer=new LcDocument();peer.setDocumentType(DocumentType.COMMERCIAL_INVOICE);peer.setOriginalFilename("synthetic-invoice.pdf");peer.setContent(new byte[]{1});peer.setExtractedDocumentNumber(value);
   peer.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_PACKAGE_COUNT,"12",PackDefinition.Field.DOCUMENT_SHIPPING_MARKS,value),true));
   assertThat(service.evaluate(new LetterOfCredit(),List.of(doc,peer)).get(0).severity().name()).isEqualTo("OK");
   var duplicate=new LcDocument();duplicate.setDocumentType(DocumentType.COMMERCIAL_INVOICE);
   assertThat(service.evaluate(new LetterOfCredit(),List.of(doc,peer,duplicate)).get(0).message()).contains("nicht prüfbar");
  }
 }
 @Test void literalRuntimeUsesTheConfiguredValueAndReportsIt(){
  var rule=new PackDefinition.Rule("synthetic-literal","1.0.0",DocumentType.SEA_WAYBILL,PackDefinition.Field.DOCUMENT_SIGNED,PackDefinition.Operator.EQ,PackDefinition.Field.LITERAL,PackDefinition.Level.DISCREPANCY,"Synthetic signature","Internal synthetic",PackDefinition.Mode.AUTOMATIC,null,null,"true");
  var tests=List.of(new PackDefinition.TestCase("pass",rule.id(),"true",null,PackDefinition.Outcome.PASS),new PackDefinition.TestCase("fail",rule.id(),"false",null,PackDefinition.Outcome.FAIL),new PackDefinition.TestCase("missing",rule.id(),null,null,PackDefinition.Outcome.NOT_EVALUABLE));
  var pack=new PackDefinition(5,"synthetic-literal","1.0.0","Synthetic literal","OWN_INTERNAL","Internal","Synthetic only",List.of(rule),tests);codec.validate(pack);
  var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
  var doc=new LcDocument();doc.setDocumentType(DocumentType.SEA_WAYBILL);doc.setOriginalFilename("synthetic.pdf");doc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_SIGNED,"false"),true));
  var result=service.evaluate(new LetterOfCredit(),List.of(doc)).get(0);
  assertThat(result.severity().name()).isEqualTo("DISCREPANCY");assertThat(result.lcCondition()).contains("LITERAL = true");
  doc.setRuleFactsJson("{}");assertThat(service.evaluate(new LetterOfCredit(),List.of(doc)).get(0).message()).contains("nicht prüfbar");
 }
 @Test void corruptedPackNeverProducesSuccessfulFinding()throws Exception{
  var v=version();v.definitionJson+=" ";var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;
  when(selections.findAll()).thenReturn(List.of(selected));
  assertThat(service.evaluate(new LetterOfCredit(),List.of()).get(0).code()).isEqualTo("INTERNAL_PACK_INVALID");
 }
 @Test void schemaTwoFindingsIncludeScopeFactsAndDoNotTurnManualOrExcludedRulesGreen()throws Exception{
  byte[] source;try(var input=getClass().getResourceAsStream("/static/rule-pack-example-v2.json")){source=input.readAllBytes();}
  var pack=codec.parse(source);var v=new StoredPackVersion();v.packId=pack.packId();v.version=pack.version();v.definitionJson=codec.canonical(pack);v.checksum=codec.digest(v.definitionJson);v.testsPassed=true;
  when(versions.findById(v.id)).thenReturn(Optional.of(v));var selected=new PackSelection();selected.id=v.packId;selected.activeVersionId=v.id;when(selections.findAll()).thenReturn(List.of(selected));
  var lc=new LetterOfCredit();lc.setBeneficiary("DEMO EXPORT");lc.setApplicant("DEMO IMPORT");
  lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_TRANSFERRED,"false",PackDefinition.Field.LC_GOODS_DESCRIPTION,"Test items"),false));
  var doc=new LcDocument();doc.setDocumentType(DocumentType.COMMERCIAL_INVOICE);doc.setOriginalFilename("synthetic.pdf");
  doc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.DOCUMENT_ISSUER,"DEMO EXPORT",PackDefinition.Field.DOCUMENT_RECIPIENT,"DEMO IMPORT",PackDefinition.Field.DOCUMENT_GOODS_DESCRIPTION,"Test items"),true));
  var findings=service.evaluate(lc,List.of(doc));assertThat(findings.get(0).severity().name()).isEqualTo("OK");
  assertThat(findings.get(2).severity().name()).isEqualTo("WARNING");assertThat(findings.get(2).message()).contains("Manuelle");
  var fingerprint=findings.get(0).reviewFingerprint();
  lc.setRuleFactsJson(RuleFacts.encode(Map.of(PackDefinition.Field.LC_TRANSFERRED,"true",PackDefinition.Field.LC_GOODS_DESCRIPTION,"Test items"),false));
  var excluded=service.evaluate(lc,List.of(doc));assertThat(excluded).allMatch(f->f.severity().name().equals("WARNING"));
  assertThat(excluded.get(0).message()).contains("nicht anwendbar");assertThat(excluded.get(0).reviewFingerprint()).isNotEqualTo(fingerprint);
  lc.setRuleFactsJson("{}");assertThat(service.evaluate(lc,List.of(doc))).allMatch(f->f.message().contains("nicht prüfbar"));
 }
}
