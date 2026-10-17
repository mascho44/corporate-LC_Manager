package de.ostms.lc.check.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.check.api.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DocumentPresentationCheckTest {
 LcDocument doc(Integer copy,String number){var d=new LcDocument();d.setCopyNumber(copy);d.setOriginalFilename("synthetic.pdf");d.setExtractedDocumentNumber(number);return d;}
 @Test void countsNumberedOriginalsAndCopiesSeparately(){var result=DocumentPresentationCheck.evaluate("Bill of lading in 3/3 originals and two copies",List.of(doc(-1,"BL1"),doc(-2,"BL1"),doc(-3,"BL1"),doc(1,"BL1"),doc(2,"BL1")));assertThat(result).hasSize(2).allMatch(r->r.severity()==CheckResult.Severity.OK);}
 @Test void duplicatesAndDifferentDocumentNumbersDoNotProduceFalsePass(){assertThat(DocumentPresentationCheck.evaluate("three originals",List.of(doc(0,"BL1"),doc(0,"BL1"),doc(0,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.OK);assertThat(DocumentPresentationCheck.evaluate("two originals",List.of(doc(-1,"BL1"),doc(-2,"BL2")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);}
 @Test void unknownIsNotAnOriginalAndDeficitIsExplicit(){assertThat(DocumentPresentationCheck.evaluate("3 originals",List.of(doc(null,"BL1"),doc(-1,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);assertThat(DocumentPresentationCheck.evaluate("3 originals",List.of(doc(-1,"BL1"),doc(1,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.DISCREPANCY);}
 @Test void applicationCountsPlainOriginalsAndCopies(){assertThat(DocumentPresentationCheck.evaluate("two originals",List.of(doc(0,"BL1"),doc(0,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.OK);assertThat(DocumentPresentationCheck.evaluate("three originals",List.of(doc(0,"BL1"),doc(0,"BL1"),doc(1,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.DISCREPANCY);}
 @Test void vagueOrConflictingAmountsRequireHumanReview(){assertThat(DocumentPresentationCheck.required("full set of originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("one original or three originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("3 / 2 originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("3 / 3 originals",true)).hasValue(3);assertThat(DocumentPresentationCheck.required("drei Originale und zwei Kopien",true)).hasValue(3);}
 @Test void messagesAreGerman(){
  var warning=DocumentPresentationCheck.evaluate("two originals",List.of(doc(-1,"BL1"),doc(-2,"BL2"))).get(0);
  assertThat(warning.message()).contains("Originale: 2 erfasst / 2 gefordert").contains("Unterschiedliche oder fehlende Dokumentnummern");
  assertThat(warning.documentEvidence()).contains("Erfasste Kennzeichnungen: 2 Originale, 0 Kopien, 0 ohne Kennzeichnung").contains("von einer Person geprüft");
  var unclear=DocumentPresentationCheck.evaluate("insurance policy in duplicate",List.of(doc(0,"P1"))).get(0);
  assertThat(unclear.code()).isEqualTo("DOCUMENT_COPIES_MANUAL_REVIEW");assertThat(unclear.message()).contains("nicht eindeutig").contains("manuell prüfen");
  assertThat(DocumentPresentationCheck.evaluate("one original",List.of(doc(0,"P1"))).get(0).documentEvidence()).contains("1 Original,");
 }
 @Test void fullSetUsesTheNumberStatedOnTheBillOfLading(){
  var docs=new ArrayList<LcDocument>();
  for(int i=0;i<3;i++){var o=doc(0,"BL1");o.setExtractedText("BILL OF LADING\nNumber of original B/L: THREE (3)");docs.add(o);}
  for(int i=0;i<3;i++)docs.add(doc(1,"BL1"));
  var result=DocumentPresentationCheck.evaluate("Full set of clean on board B/L plus 3 non-negotiable copies",docs);
  assertThat(result).hasSize(2).allMatch(r->r.severity()==CheckResult.Severity.OK);
  var noStatement=DocumentPresentationCheck.evaluate("Full set of B/L",List.of(doc(0,"BL1")));
  assertThat(noStatement.get(0).code()).isEqualTo("DOCUMENT_COPIES_MANUAL_REVIEW");
 }
 // Nothing entered: the stamp text of the document counts as recognised designation; a full set is then compared with the number the bill of lading states.
 LcDocument stamped(String text,String number){var d=doc(null,number);d.setExtractedText(text);return d;}
 @Test void stampTextCountsWhenNothingWasEnteredAndIsMarkedAsRecognised(){
  String front="BILL OF LADING\nNO. OF ORIGINALS TYPE OF MOVEMENT\nWUHAN 021E 3 / THREE FCL/FCL\n";
  var originals=List.of(stamped(front+"\nORIGINAL\n","BL1"),stamped(front+"\nORIGINAL\n","BL1"),stamped(front+"\nORIGINAL\n","BL1"),stamped(front+"\nNON-NEGOTIABLE COPY\n","BL1"));
  var result=DocumentPresentationCheck.evaluate("Full set of clean on board bills of lading",originals);
  assertThat(result).hasSize(1);var r=result.get(0);
  assertThat(r.code()).isEqualTo("DOCUMENT_ORIGINAL_COUNT");assertThat(r.severity()).isEqualTo(CheckResult.Severity.OK);
  assertThat(r.message()).contains("Originale: 3 erkannt / 3 gefordert").contains("Davon 4 aus dem Stempeltext erkannt");
  // only two originals found: a warning (not a discrepancy), because the stamp may simply not have been read
  var two=DocumentPresentationCheck.evaluate("Full set of clean on board bills of lading",originals.subList(0,2));
  assertThat(two.get(0).severity()).isEqualTo(CheckResult.Severity.WARNING);assertThat(two.get(0).message()).contains("Originale: 2 erkannt / 3 gefordert");
  // an entered designation still wins over the stamp text and a shortfall with only entered designations stays a discrepancy
  var entered=new ArrayList<>(List.of(doc(0,"BL1"),doc(0,"BL1")));entered.get(0).setExtractedText(front);
  assertThat(DocumentPresentationCheck.evaluate("3 originals",entered).get(0).severity()).isEqualTo(CheckResult.Severity.DISCREPANCY);
  // a document with neither designation nor stamp keeps the result uncertain
  assertThat(DocumentPresentationCheck.evaluate("3 originals",List.of(stamped("ORIGINAL","BL1"),stamped("nothing here","BL1"))).get(0).severity()).isEqualTo(CheckResult.Severity.WARNING);
 }
}
