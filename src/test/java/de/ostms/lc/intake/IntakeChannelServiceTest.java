package de.ostms.lc.intake;

import de.ostms.lc.audit.service.AuditService;
import de.ostms.lc.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntakeChannelServiceTest {
 @TempDir Path root;
 final IntakeChannelRepository repo=Mockito.mock(IntakeChannelRepository.class);final InboxIntakeService intake=Mockito.mock(InboxIntakeService.class);final AuditService audit=Mockito.mock(AuditService.class);
 final org.springframework.security.core.Authentication admin=new TestingAuthenticationToken("admin","x");
 IntakeChannelService service(){var folder=new FolderIntakeChannel(root.toString(),0);return new IntakeChannelService(repo,intake,audit,List.of(folder),folder);}

 @Test void aFolderChannelIsCreatedWithItsFoldersAndAnAuditEntry()throws Exception{
  try(var scope=TenantContext.open(UUID.randomUUID())){
   when(repo.byName("poststelle")).thenReturn(Optional.empty());when(repo.save(any())).thenAnswer(c->c.getArgument(0));
   var view=service().create(new IntakeChannelService.Request("FOLDER","poststelle",true,5),admin);
   assertThat(view.name()).isEqualTo("poststelle");assertThat(view.enabled()).isTrue();assertThat(view.intervalMinutes()).isEqualTo(5);assertThat(view.folder()).startsWith(root.toString()).endsWith("eingang");
   assertThat(root.resolve(view.folder()).toFile()).isDirectory();
   verify(audit).record(eq(admin),eq("INTAKE_CHANNEL_CREATED"),eq("INTAKE_CHANNEL"),any(),anyString());
  }
 }
 @Test void unknownTypesDuplicateNamesBadNamesAndIntervalsAreRefused()throws Exception{
  try(var scope=TenantContext.open(UUID.randomUUID())){
   var s=service();
   assertThatThrownBy(()->s.create(new IntakeChannelService.Request("FAX","x",true,2),admin)).hasMessageContaining("Kanaltyp");
   when(repo.byName("vorhanden")).thenReturn(Optional.of(new IntakeChannelConfig("FOLDER","vorhanden",true,2,"a")));
   assertThatThrownBy(()->s.create(new IntakeChannelService.Request("FOLDER","vorhanden",true,2),admin)).hasMessageContaining("schon einen Kanal");
   when(repo.byName("../x")).thenReturn(Optional.empty());
   assertThatThrownBy(()->s.create(new IntakeChannelService.Request("FOLDER","../x",true,2),admin)).isInstanceOf(IllegalArgumentException.class);
   assertThatThrownBy(()->s.create(new IntakeChannelService.Request("FOLDER","ok",true,0),admin)).hasMessageContaining("Intervall");
   assertThatThrownBy(()->s.create(new IntakeChannelService.Request("FOLDER","ok",true,1441),admin)).hasMessageContaining("Intervall");
   verify(repo,never()).save(any());
  }
 }
 @Test void aFailingChannelRunIsRecordedAsErrorInsteadOfEscaping()throws Exception{
  try(var scope=TenantContext.open(UUID.randomUUID())){
   var c=new IntakeChannelConfig("FOLDER","poststelle",true,2,"admin");when(repo.findAll()).thenReturn(List.of(c));when(repo.save(any())).thenAnswer(x->x.getArgument(0));
   var broken=new IntakeChannel(){public String type(){return "FOLDER";}public Result poll(IntakeChannelConfig config,IntakeSink sink){throw new IllegalStateException("Ordner nicht lesbar");}};
   var folder=new FolderIntakeChannel(root.toString(),0);
   new IntakeChannelService(repo,intake,audit,List.of(broken),folder).pollDue();
   assertThat(c.getLastResult()).isEqualTo("Fehler: Ordner nicht lesbar");assertThat(c.getLastRunAt()).isNotNull();
   // switched off: not run at all
   var off=new IntakeChannelConfig("FOLDER","aus",false,2,"admin");when(repo.findAll()).thenReturn(List.of(off));
   new IntakeChannelService(repo,intake,audit,List.of(broken),folder).pollDue();assertThat(off.getLastRunAt()).isNull();
  }
 }
}
