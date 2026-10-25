package de.ostms.lc.intake;

import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FolderIntakeChannelTest {
 @TempDir Path root;
 final UUID tenant=UUID.randomUUID();
 final List<IntakeItem> delivered=new ArrayList<>();
 IntakeChannel.IntakeSink sink(boolean duplicate){return items->{delivered.addAll(items);return items.stream().map(i->new IntakeReceipt(i.filename(),duplicate?IntakeReceipt.DUPLICATE:IntakeReceipt.CREATED,UUID.randomUUID(),"x",null,null,true,null)).toList();};}
 IntakeChannelConfig config(){return new IntakeChannelConfig(FolderIntakeChannel.TYPE,"poststelle",true,2,"admin");}
 FolderIntakeChannel channel(long settle){return new FolderIntakeChannel(root.toString(),settle);}
 long count(Path dir)throws Exception{try(var s=Files.list(dir)){return s.count();}}

 @Test void deliveredFilesMoveToDoneAndRefusedOnesToFailedWithTheReason()throws Exception{
  try(var scope=TenantContext.open(tenant)){
   var channel=channel(0);var folders=channel.folders(tenant,"poststelle");
   Files.writeString(folders.in().resolve("rechnung.pdf"),"PDF");Files.writeString(folders.in().resolve("notiz.docx"),"WORD");Files.write(folders.in().resolve("leer.pdf"),new byte[0]);
   Files.writeString(folders.in().resolve(".versteckt.pdf"),"x");Files.writeString(folders.in().resolve("halb.pdf.part"),"x");
   var result=channel.poll(config(),sink(false));
   assertThat(result.created()).isEqualTo(1);assertThat(result.failed()).isEqualTo(2);assertThat(result.duplicates()).isZero();
   assertThat(delivered).hasSize(1);var item=delivered.get(0);
   assertThat(item.filename()).isEqualTo("rechnung.pdf");assertThat(item.contentType()).isEqualTo("application/pdf");
   assertThat(item.origin().channel()).isEqualTo("FOLDER");assertThat(item.origin().reference()).isEqualTo("poststelle/rechnung.pdf");assertThat(item.origin().actor()).isEqualTo("folder:poststelle");
   assertThat(count(folders.done())).isEqualTo(1);
   try(var s=Files.list(folders.failed())){var names=s.map(p->p.getFileName().toString()).toList();assertThat(names).anyMatch(n->n.endsWith("notiz.docx")).anyMatch(n->n.endsWith("notiz.docx.fehler.txt")).anyMatch(n->n.endsWith("leer.pdf"));}
   assertThat(count(folders.in())).isEqualTo(2); // hidden and half-written files stay where they are
   assertThat(result.summary()).contains("1 neu").contains("2 fehlgeschlagen");
  }
 }
 @Test void aDuplicateIsStillMovedAwayAndCounted()throws Exception{
  try(var scope=TenantContext.open(tenant)){
   var channel=channel(0);var folders=channel.folders(tenant,"poststelle");Files.writeString(folders.in().resolve("a.pdf"),"PDF");
   var result=channel.poll(config(),sink(true));
   assertThat(result.duplicates()).isEqualTo(1);assertThat(result.created()).isZero();assertThat(count(folders.in())).isZero();assertThat(count(folders.done())).isEqualTo(1);
  }
 }
 @Test void aFileTheScannerIsStillWritingIsLeftAloneAndAFailingDeliveryMovesItToFailed()throws Exception{
  try(var scope=TenantContext.open(tenant)){
   var folders=channel(3600).folders(tenant,"poststelle");Files.writeString(folders.in().resolve("neu.pdf"),"PDF");
   var waiting=channel(3600).poll(config(),sink(false));
   assertThat(waiting.waiting()).isEqualTo(1);assertThat(delivered).isEmpty();assertThat(count(folders.in())).isEqualTo(1);
   var failing=channel(0).poll(config(),items->{throw new IllegalArgumentException("Prüfwert falsch");});
   assertThat(failing.failed()).isEqualTo(1);assertThat(count(folders.in())).isZero();
   try(var s=Files.list(folders.failed())){assertThat(s.map(p->p.getFileName().toString()).toList()).anyMatch(n->n.endsWith(".fehler.txt"));}
  }
 }
 @Test void namesAreRestrictedSoNoPathCanBeBuiltFromThem()throws Exception{
  var channel=channel(0);
  for(String bad:new String[]{"../x","a/b","","A","-x","x".repeat(41),"a b",".."})assertThatThrownBy(()->channel.folders(tenant,bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
  assertThat(channel.folders(tenant,"scanner-1").in()).startsWith(root);
 }
 @Test void symbolicLinksAreIgnored()throws Exception{
  Assumptions.assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));
  try(var scope=TenantContext.open(tenant)){
   var channel=channel(0);var folders=channel.folders(tenant,"poststelle");var outside=Files.writeString(root.resolve("geheim.pdf"),"GEHEIM");
   Files.createSymbolicLink(folders.in().resolve("link.pdf"),outside);
   var result=channel.poll(config(),sink(false));
   assertThat(result.created()+result.failed()+result.duplicates()).isZero();assertThat(delivered).isEmpty();
  }
 }
 @Test void aChannelIsDueWhenOnAndItsIntervalHasPassed(){
  var c=config();var now=java.time.LocalDateTime.of(2026,10,17,10,0);
  assertThat(c.due(now)).isTrue();c.recordRun("ok",now);assertThat(c.due(now.plusMinutes(1))).isFalse();assertThat(c.due(now.plusMinutes(2))).isTrue();
  c.update(false,2);assertThat(c.due(now.plusHours(1))).isFalse();
 }
}
