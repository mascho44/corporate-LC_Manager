package de.ostms.lc.document.service;

import de.ostms.lc.document.domain.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeTrainingServiceTest {
 JdbcTemplate jdbc;DocumentTypeTrainingService service;
 static final String DRAFT_ONE="Wechsel Zahlen Sie gegen diesen Wechsel an die Order von Beispiel Handel GmbH den Betrag in Worten Aussteller Bezogener Zahlungsort Verfalltag Unterschrift Stempel Original";
 static final String DRAFT_TWO="Wechsel Gegen diesen Wechsel zahlen Sie an die Order der Muster Export AG den Betrag in Worten Aussteller Bezogener Zahlungsort Verfalltag Unterschrift Original";
 static final String OTHER_KIND="Packing list cartons gross weight net weight dimensions marks numbers container seal vessel voyage consignee notify party description of goods";

 @BeforeEach void setUp(){
  var source=new DriverManagerDataSource("jdbc:h2:mem:doctypetraining"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
  jdbc=new JdbcTemplate(source);
  jdbc.execute("create table document_type_example(id uuid primary key,tenant_id uuid not null,document_type varchar(60) not null,terms text not null,sample varchar(200),confirmed_by varchar(255) not null,header_terms text,confirmed_at timestamp with time zone default current_timestamp)");
  service=new DocumentTypeTrainingService(jdbc);
 }

 @Test void similarityIsOverlapOfWordSetsAndIgnoresNumbersAndShortWords(){
  assertThat(DocumentTypeTrainingService.words("Invoice 123 of 45 EUR, Invoice")).containsExactly("invoice","eur");
  assertThat(DocumentTypeTrainingService.similarity(DocumentTypeTrainingService.words("alpha beta gamma"),DocumentTypeTrainingService.words("alpha beta gamma"))).isEqualTo(1.0);
  assertThat(DocumentTypeTrainingService.similarity(DocumentTypeTrainingService.words("alpha beta"),DocumentTypeTrainingService.words("delta epsilon"))).isZero();
 }

 @Test void learnedExamplesRecogniseSimilarTextsButNotOtherKindsAndNothingIsStoredTwiceOrWithoutEnoughText(){
  assertThat(service.learn("zu wenig Text hier",DocumentType.BILL_OF_EXCHANGE,"anna")).isFalse();
  assertThat(service.learn(DRAFT_ONE,DocumentType.OTHER,"anna")).isFalse();
  assertThat(service.learn(DRAFT_ONE,DocumentType.BILL_OF_EXCHANGE,"anna")).isTrue();
  assertThat(service.learn(DRAFT_ONE+" 2026",DocumentType.BILL_OF_EXCHANGE,"anna")).isFalse();
  var match=service.match(DRAFT_TWO).orElseThrow();
  assertThat(match.type()).isEqualTo(DocumentType.BILL_OF_EXCHANGE);assertThat(match.similarity()).isGreaterThan(.6);
  assertThat(service.match(OTHER_KIND)).isEmpty();
  assertThat(service.examples()).hasSize(1).first().satisfies(e->{assertThat(e.type()).isEqualTo(DocumentType.BILL_OF_EXCHANGE);assertThat(e.confirmedBy()).isEqualTo("anna");});
 }

 @Test void whenTwoTypesResembleTheTextEquallyNothingIsSuggested(){
  service.learn(DRAFT_ONE,DocumentType.BILL_OF_EXCHANGE,"anna");
  service.learn(DRAFT_ONE.replace("Stempel","Siegel"),DocumentType.INSURANCE_CERTIFICATE,"anna");
  assertThat(service.match(DRAFT_ONE)).isEmpty();
 }

 @Test void unknownPartsGetTheLearnedTypeAsAReviewSuggestionAndRecognisedPartsStayAsTheyAre(){
  service.learn(DRAFT_ONE,DocumentType.BILL_OF_EXCHANGE,"anna");
  var unknown=new DocumentClassifier.Classification(null,0,"UNKNOWN","NONE",List.of());
  var known=new DocumentClassifier.Classification(DocumentType.PACKING_LIST,.95,"CONFIRMED","HEADING",List.of());
  var baseline=new PdfDocumentSplitter.Proposal(2,List.of(new PdfDocumentSplitter.Page(1,unknown,"PAGE_TEXT"),new PdfDocumentSplitter.Page(2,known,"PAGE_TEXT")),
   List.of(new PdfDocumentSplitter.Part(1,1,DocumentType.OTHER),new PdfDocumentSplitter.Part(2,2,DocumentType.PACKING_LIST)));
  var result=service.apply(baseline,List.of(DRAFT_TWO,OTHER_KIND));
  assertThat(result.parts().get(0).documentType()).isEqualTo(DocumentType.BILL_OF_EXCHANGE);assertThat(result.parts().get(1).documentType()).isEqualTo(DocumentType.PACKING_LIST);
  assertThat(result.pages().get(0).classification().method()).isEqualTo("TRAINED_TYPE_V1");assertThat(result.pages().get(0).classification().status()).isEqualTo("REVIEW");
  assertThat(result.pages().get(0).classification().evidence().get(0)).contains("Aus Training").contains("bitte prüfen");
  assertThat(result.pages().get(1).classification().method()).isEqualTo("HEADING");
  assertThat(service.apply(baseline,List.of(OTHER_KIND,OTHER_KIND))).isSameAs(baseline);
 }

 @Test void examplesCanBeRemoved(){
  service.learn(DRAFT_ONE,DocumentType.BILL_OF_EXCHANGE,"anna");
  var id=service.examples().get(0).id();
  assertThat(service.delete(id)).isTrue();assertThat(service.delete(id)).isFalse();assertThat(service.match(DRAFT_TWO)).isEmpty();
 }

 static final String LETTERHEAD="Nordwind Maschinenbau GmbH\nIndustriestrasse Bremen Germany\nTelefon Telefax Handelsregister\nAmtsgericht Steuernummer\nBankverbindung Sparkasse\n";
 @Test void aKnownLetterheadIsASafeLayoutMatchAndImprovesAWeakSuggestion(){
  assertThat(service.learn(LETTERHEAD+"Rechnung\nLieferung\nErsatzteile\nPumpengehaeuse\nDichtungen\nMontagematerial\nPositionen Mengen\nEinzelpreise\nGesamtbetrag\nZahlungsbedingungen",DocumentType.COMMERCIAL_INVOICE,"anna")).isTrue();
  // Same issuer, a different order with entirely other positions.
  String next=LETTERHEAD+"Rechnung\nWerkzeugsatz\nSchraubenschluessel\nKalibrierung\nPruefprotokoll\nVerpackung\nSeefracht\nHamburg Shanghai\nKonnossement\nZahlungsziel";
  var match=service.match(next).orElseThrow();
  assertThat(match.layout()).isTrue();assertThat(match.type()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);assertThat(match.confirmedBy()).isEqualTo("anna");assertThat(match.examples()).isEqualTo(1);
  assertThat(ClassificationHistory.suggestion(service.improve(ClassificationHistory.automatic(null,next),next)).status()).isEqualTo("REVIEW");
  // A second confirmed invoice of this issuer makes the layout a safe suggestion.
  assertThat(service.learn(LETTERHEAD+"Rechnung\nWartungsvertrag\nServicetechniker\nAnfahrt\nArbeitsstunden\nVerschleissteile\nSchmierstoffe\nPruefung\nAbnahme\nSkonto",DocumentType.COMMERCIAL_INVOICE,"bob")).isTrue();
  String weak=ClassificationHistory.automatic(null,next);
  String improved=service.improve(weak,next);
  var suggestion=ClassificationHistory.suggestion(improved);
  assertThat(suggestion.suggestedType()).isEqualTo(DocumentType.COMMERCIAL_INVOICE);assertThat(suggestion.method()).isEqualTo("TRAINED_LAYOUT_V1");assertThat(suggestion.status()).isEqualTo("SUGGESTED");
  assertThat(suggestion.evidence().get(0)).contains("Briefkopf","2 bestätigte Beispiele");
  // A confirmed type is never replaced.
  String manual=ClassificationHistory.manual(weak,DocumentType.PACKING_LIST,"bob");
  assertThat(service.improve(manual,next)).isEqualTo(manual);
 }
}
