package de.ostms.lc.check.service;
import de.ostms.lc.document.domain.*;
import de.ostms.lc.check.api.CheckResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DocumentPresentationCheckTest {
 LcDocument doc(Integer copy,String number){var d=new LcDocument();d.setCopyNumber(copy);d.setOriginalFilename("synthetic.pdf");d.setExtractedDocumentNumber(number);return d;}
 @Test void countsNumberedOriginalsAndCopiesSeparately(){var result=DocumentPresentationCheck.evaluate("Bill of lading in 3/3 originals and two copies",List.of(doc(-1,"BL1"),doc(-2,"BL1"),doc(-3,"BL1"),doc(1,"BL1"),doc(2,"BL1")));assertThat(result).hasSize(2).allMatch(r->r.severity()==CheckResult.Severity.OK);}
 @Test void duplicatesAndDifferentDocumentNumbersDoNotProduceFalsePass(){assertThat(DocumentPresentationCheck.evaluate("three originals",List.of(doc(-1,"BL1"),doc(-1,"BL1"),doc(-2,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);assertThat(DocumentPresentationCheck.evaluate("two originals",List.of(doc(-1,"BL1"),doc(-2,"BL2")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);}
 @Test void unknownIsNotAnOriginalAndDeficitIsExplicit(){assertThat(DocumentPresentationCheck.evaluate("3 originals",List.of(doc(null,"BL1"),doc(-1,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);assertThat(DocumentPresentationCheck.evaluate("3 originals",List.of(doc(-1,"BL1"),doc(1,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.DISCREPANCY);}
 @Test void repeatedUnnumberedOriginalsNeedReview(){assertThat(DocumentPresentationCheck.evaluate("two originals",List.of(doc(0,"BL1"),doc(0,"BL1")))).allMatch(r->r.severity()==CheckResult.Severity.WARNING);}
 @Test void vagueOrConflictingAmountsRequireHumanReview(){assertThat(DocumentPresentationCheck.required("full set of originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("one original or three originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("3 / 2 originals",true)).isEmpty();assertThat(DocumentPresentationCheck.required("3 / 3 originals",true)).hasValue(3);assertThat(DocumentPresentationCheck.required("drei Originale und zwei Kopien",true)).hasValue(3);}
}
