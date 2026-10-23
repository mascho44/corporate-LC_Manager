package de.ostms.lc.swiftfolder;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.ebics.EbicsMessage;
import de.ostms.lc.ebics.EbicsMessageRepository;
import de.ostms.lc.imports.service.SwiftImportService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SwiftFolderTest {
 static final String MT700=":20:LC2026001\n:31D:261218FRANKFURT\n:32B:EUR125000,00\n:59:SUPPLIER";
 static final String MT707=":20:LC2026001\n:21:LC2026001\n:26E:1";

 @Test void splitsPlainBlockAndRjeFiles() {
  assertThat(SwiftFileSplitter.split(MT700)).hasSize(1);
  String blocks="{1:F01BANKDEFFAXXX0000000000}{2:O7001200261022BANKDEFFAXXX00000000002610221200N}{4:\n:20:A1\n:32B:EUR1,\n-}{5:{CHK:123}}\n{1:F01BANKDEFFAXXX0000000000}{2:O7071200261022BANKDEFFAXXX00000000002610221200N}{4:\n:20:A2\n:21:A1\n:26E:1\n-}";
  var two=SwiftFileSplitter.split(blocks);assertThat(two).hasSize(2);assertThat(two.get(0)).contains(":20:A1").doesNotContain(":20:A2");assertThat(two.get(1)).contains(":20:A2");
  String rje="\u0001{1:F01BANKDEFFAXXX0000000000}{4:\r\n:20:R1\r\n:32B:EUR1,\r\n-}\u0003\r\n$\r\n\u0001{1:F01BANKDEFFAXXX0000000000}{4:\r\n:20:R2\r\n-}\u0003";
  assertThat(SwiftFileSplitter.split(rje)).hasSize(2);
  String dollar=":20:D1\n:32B:EUR1,\n$\n:20:D2\n:21:D1\n:26E:1\n$\n";
  assertThat(SwiftFileSplitter.split(dollar)).hasSize(2);
  assertThat(SwiftFileSplitter.split("   \n ")).isEmpty();
 }

 private final EbicsMessageRepository messages=mock(EbicsMessageRepository.class);private final SwiftImportService swift=mock(SwiftImportService.class);
 private final AuditService audit=mock(AuditService.class);private final TenantRepository tenants=mock(TenantRepository.class);
 private final UUID tenantId=UUID.randomUUID();
 private SwiftFolderInbox inbox(Path root,boolean enabled){
  when(swift.detect(anyString())).thenAnswer(i->{String t=i.getArgument(0);return t.contains(":26E:")?"MT707":t.contains("TYPE-X")?"MT999":"MT700";});
  when(messages.save(any())).thenAnswer(i->i.getArgument(0));
  return new SwiftFolderInbox(enabled,root.toString(),0,messages,swift,audit,tenants);
 }
 private void bankTenant(boolean bank){var t=mock(Tenant.class);when(t.isActive()).thenReturn(true);when(t.isBankEnabled()).thenReturn(bank);when(tenants.findById(tenantId)).thenReturn(Optional.of(t));}
 private static Path put(Path dir,String name,String content)throws Exception{Files.createDirectories(dir);var p=dir.resolve(name);Files.writeString(p,content,StandardCharsets.UTF_8);return p;}

 @Test void deliversStoresOnceAndMovesTheFilesWithoutImportingAnything(@TempDir Path root)throws Exception {
  bankTenant(true);var inbox=inbox(root,true);var folders=inbox.folders(tenantId);
  put(folders.in(),"a.fin",MT700+"\n$\n"+MT707);put(folders.in(),"dup.txt",MT700);put(folders.in(),".hidden",MT700);put(folders.in(),"x.tmp",MT700);
  var saved=new ArrayList<EbicsMessage>();when(messages.save(any())).thenAnswer(i->{saved.add(i.getArgument(0));return i.getArgument(0);});
  when(messages.findBySha(anyString())).thenAnswer(i->saved.stream().filter(m->m.getSha256().equals(i.getArgument(0))).findFirst()); // like the database: a stored message is known
  try(var scope=TenantContext.open(tenantId)){
   inbox.pollCurrentTenant();
   assertThat(saved).extracting(EbicsMessage::getMessageType).containsExactly("MT700","MT707");
   assertThat(saved).allSatisfy(m->{assertThat(m.getSource()).isEqualTo("FOLDER");assertThat(m.getStatus()).isEqualTo("NEW");assertThat(m.getOrigin()).isEqualTo("a.fin");});
   assertThat(inbox.status().lastResult()).isEqualTo("2 neu, 1 bereits bekannt, 0 fehlgeschlagen"); // dup.txt repeats the MT700 of a.fin
  }
  try(var list=Files.list(folders.done())){assertThat(list.map(p->p.getFileName().toString()).toList()).hasSize(2).allMatch(n->n.endsWith("-a.fin")||n.endsWith("-dup.txt"));}
  try(var list=Files.list(folders.in())){assertThat(list.map(p->p.getFileName().toString()).toList()).containsExactlyInAnyOrder(".hidden","x.tmp");}
  verify(audit,times(2)).record(eq("system:swift-folder"),eq("SWIFT_FOLDER_RECEIVED"),anyString(),any(),anyString(),eq(true),isNull());
 }

 @Test void refusesBadFilesWithAReasonAndNeverDeletesThem(@TempDir Path root)throws Exception {
  bankTenant(true);var inbox=inbox(root,true);var folders=inbox.folders(tenantId);
  put(folders.in(),"empty.txt","");put(folders.in(),"noswift.txt","Hallo Welt");put(folders.in(),"unknown.txt",":20:X1\nTYPE-X\n:32B:EUR1,");
  Files.write(folders.in().resolve("big.fin"),new byte[(int)SwiftFolderInbox.MAX_BYTES+1]);Files.write(folders.in().resolve("binary.bin"),new byte[]{0x00,(byte)0xff,(byte)0xfe,0x01});
  try(var scope=TenantContext.open(tenantId)){inbox.pollCurrentTenant();}
  try(var list=Files.list(folders.in())){assertThat(list.toList()).isEmpty();}
  try(var list=Files.list(folders.failed())){
   var names=list.map(p->p.getFileName().toString()).toList();
   assertThat(names).hasSize(10); // five files, each with a .fehler.txt
   assertThat(names.stream().filter(n->n.endsWith(".fehler.txt"))).hasSize(5);
  }
  verify(messages,never()).save(any());
  var reasons=new ArrayList<String>();try(var list=Files.list(folders.failed())){for(var p:list.filter(p->p.toString().endsWith(".fehler.txt")).toList())reasons.add(Files.readString(p));}
  assertThat(String.join("|",reasons)).contains("leer").contains("512 KB").contains("MT999").contains("ohne SWIFT-Felder").contains("kein gültiger Text");
 }

 @Test void doesNothingWhenSwitchedOffAndOnlyForBankTenantsAndIgnoresSymbolicLinks(@TempDir Path root)throws Exception {
  bankTenant(true);var off=inbox(root,false);
  try(var scope=TenantContext.open(tenantId)){off.pollCurrentTenant();assertThat(off.status().enabled()).isFalse();}
  assertThat(Files.exists(root.resolve(tenantId.toString()))).isFalse();
  bankTenant(false);var corporate=inbox(root,true);
  try(var scope=TenantContext.open(tenantId)){corporate.pollCurrentTenant();}
  assertThat(Files.exists(root.resolve(tenantId.toString()))).isFalse();
  bankTenant(true);var inbox=inbox(root,true);var folders=inbox.folders(tenantId);
  var outside=put(root.resolve("outside"),"secret.txt",MT700);
  try{Files.createSymbolicLink(folders.in().resolve("link.txt"),outside);}catch(UnsupportedOperationException|java.io.IOException skip){return;}
  try(var scope=TenantContext.open(tenantId)){inbox.pollCurrentTenant();}
  verify(messages,never()).save(any());assertThat(Files.exists(outside)).isTrue();
 }

 @Test void theStatusNamesThePathOfTheTenant(@TempDir Path root)throws Exception {
  var inbox=inbox(root,true);
  try(var scope=TenantContext.open(tenantId)){assertThat(inbox.status().path()).isEqualTo(root.toAbsolutePath().normalize().resolve(tenantId.toString()).resolve("eingang").toString());}
 }
}
