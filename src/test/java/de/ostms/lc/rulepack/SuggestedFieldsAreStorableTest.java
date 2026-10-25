package de.ostms.lc.rulepack;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.assertj.core.api.Assertions.assertThat;

/** A suggestion for a field that RuleFacts does not accept would make "Erkannte Prüfdaten übernehmen" fail for the whole file. */
class SuggestedFieldsAreStorableTest {
 private static Set<String> used(String... files)throws Exception{
  var out=new TreeSet<String>();
  for(String f:files){var m=Pattern.compile("Field\\.([A-Z_]{4,})").matcher(Files.readString(Path.of("src/main/java/de/ostms/lc/rulepack/"+f)));while(m.find())out.add(m.group(1));}
  return out;
 }
 private static Set<String> names(Set<PackDefinition.Field> fields){var s=new TreeSet<String>();fields.forEach(f->s.add(f.name()));return s;}

 @Test void documentSuggestersOnlyProposeEditableDocumentFacts()throws Exception{
  var used=used("DocumentFactSuggester.java","DocumentTextFacts.java","DocumentIndicators.java","PackingListFacts.java","DocumentPartyFacts.java");
  used.removeIf(n->!n.startsWith("DOCUMENT_"));
  assertThat(used).isSubsetOf(names(RuleFacts.DOCUMENT));
 }
 @Test void lcSuggestersOnlyProposeEditableLcFacts()throws Exception{
  var used=used("LcFactSuggester.java","Mt700Facts.java");
  used.removeIf(n->!n.startsWith("LC_"));
  assertThat(used).isSubsetOf(names(RuleFacts.LC));
 }
}
