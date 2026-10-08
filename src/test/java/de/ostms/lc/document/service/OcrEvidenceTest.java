package de.ostms.lc.document.service;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class OcrEvidenceTest {
 @Test void gluedSwiftTagUsesWholeWordMeasurementButDoesNotCrossTags(){var evidence=new OcrEvidence("5","V2",200,.8,List.of(word(":20:LC123456",.87),word(":32B:",.4),word("EUR1000,",.65),word("00",.95)));assertThat(evidence.assess("LC123456",.8).score()).isEqualTo(.87);assertThat(evidence.assess("EUR1000,00",.8).score()).isEqualTo(.65);assertThat(evidence.assess("LC123456EUR1000,00",.8).score()).isNull();}
 private OcrEvidence.Word word(String text,double score){return new OcrEvidence.Word(text,score,2,10,20,30,40);}
 @Test void parsesOnlyWordsAndPreservesActualPdfPage(){
  String tsv="level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n5\t1\t1\t1\t1\t1\t10\t20\t30\t40\t87.5\tABC\n4\t1\t1\t1\t1\t0\t0\t0\t20\t20\t-1\tLINE";
  var words=OcrEvidence.parseTsv(tsv,12);assertThat(words).hasSize(1);assertThat(words.get(0).page()).isEqualTo(12);assertThat(words.get(0).confidence()).isEqualTo(.875);
 }
 @Test void weakestWordControlsReviewAndMeanIsSeparate(){var evidence=new OcrEvidence("tesseract 5","TESSERACT_WORD_MIN_V1",200,.8,List.of(word("BANK",.99),word("NAME",.4)));var score=evidence.assess("BANK\nNAME",.8);assertThat(score.score()).isEqualTo(.4);assertThat(score.meanScore()).isCloseTo(.695,within(0.000001));assertThat(score.status()).isEqualTo("REVIEW");assertThat(score.words()).hasSize(2);}
 @Test void duplicateAndTransformedValuesAreUnknown(){var evidence=new OcrEvidence("5","V1",200,.8,List.of(word("123",.99),word("123",.2)));assertThat(evidence.assess("123",.8).score()).isNull();assertThat(evidence.assess("00123",.8).score()).isNull();}
 @Test void noSubstringMatchOrFabricatedScore(){var evidence=new OcrEvidence("5","V1",200,.8,List.of(word("LC1234",.99)));assertThat(evidence.assess("LC123",.8).status()).isEqualTo("UNAVAILABLE");}
 @Test void invalidTesseractValuesRemainUnknown(){var words=OcrEvidence.parseTsv("5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t-1\tABC",1);assertThat(words.get(0).confidence()).isNull();assertThat(new OcrEvidence("5","V1",200,.8,words).assess("ABC",.8).score()).isNull();}
}
