package de.corporate.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.document.domain.*;
import de.corporate.lc.lc.domain.LetterOfCredit;
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
