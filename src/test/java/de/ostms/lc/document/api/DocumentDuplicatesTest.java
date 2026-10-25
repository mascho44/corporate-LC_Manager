package de.ostms.lc.document.api;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DocumentDuplicatesTest {
 @Test void identicalFilesRetainOriginalAndCopyAsSeparateMembers(){
  var original=new DocumentDuplicatesController.Member(UUID.randomUUID(),"original.pdf",0);
  var copy=new DocumentDuplicatesController.Member(UUID.randomUUID(),"copy.pdf",2);
  var report=DocumentDuplicatesController.inspect(List.of(new DocumentDuplicatesController.Input(original,new byte[]{1,2}),new DocumentDuplicatesController.Input(copy,new byte[]{1,2})),false);
  assertThat(report.checked()).isEqualTo(2);assertThat(report.groups()).hasSize(1);
  assertThat(report.groups().get(0).documents()).containsExactly(original,copy);
 }
 @Test void differentContentAndMissingContentAreNotDuplicates(){
  var member=new DocumentDuplicatesController.Member(UUID.randomUUID(),"file",null);
  var report=DocumentDuplicatesController.inspect(List.of(new DocumentDuplicatesController.Input(member,new byte[]{1}),new DocumentDuplicatesController.Input(member,new byte[]{2}),new DocumentDuplicatesController.Input(member,null)),false);
  assertThat(report.checked()).isEqualTo(2);assertThat(report.groups()).isEmpty();
 }
}
