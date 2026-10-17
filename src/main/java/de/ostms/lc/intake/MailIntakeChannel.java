package de.ostms.lc.intake;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

/**
 * Mailbox as a source: unread mails of the configured folder from the listed senders; their attachments (PDF, PNG, JPG, TIF up to 10 MB) go to the document
 * inbox, the mail is then marked as read and moved to the "processed" folder. Mails from other senders are moved to the "rejected" folder without reading
 * their attachments. A mail that could not be delivered stays unread and is tried again at the next run.
 */
@Component
public class MailIntakeChannel implements IntakeChannel {
 public static final String TYPE="EMAIL";
 static final int MAX_MAILS_PER_RUN=20;
 private final MailboxConnector connector;private final IntakeCipher cipher;private final MailHostPolicy hosts;
 public MailIntakeChannel(MailboxConnector connector,IntakeCipher cipher,MailHostPolicy hosts){this.connector=connector;this.cipher=cipher;this.hosts=hosts;}

 @Override public String type(){return TYPE;}

 private MailboxClient open(IntakeChannelConfig config)throws IOException{
  if(config.getSettings()==null||!config.hasSecret())throw new IOException("Das Postfach ist nicht vollständig eingerichtet.");
  var settings=MailboxSettings.fromJson(config.getSettings());
  hosts.check(settings.host());
  return connector.open(settings,cipher.decrypt(config.getId().toString(),config.getSecret()));
 }

 /** Connection test: number of unread mails; nothing is read or changed. */
 public int test(IntakeChannelConfig config)throws IOException{
  try(var client=open(config)){return client.unseen(1000).size();}
 }

 @Override public Result poll(IntakeChannelConfig config,IntakeSink sink)throws IOException{
  if(config.getSettings()==null||!config.hasSecret())throw new IOException("Das Postfach ist nicht vollständig eingerichtet.");
  var settings=MailboxSettings.fromJson(config.getSettings());
  int created=0,duplicates=0,failed=0,rejected=0,withoutAttachment=0;
  try(var client=open(config)){
   for(var mail:client.unseen(MAX_MAILS_PER_RUN)){
    if(mail.tooLarge()){client.finish(mail,settings.rejectedFolder());rejected++;continue;}
    if(!settings.allows(mail.from())){client.finish(mail,settings.rejectedFolder());rejected++;continue;}
    var items=new ArrayList<IntakeItem>();
    for(var a:mail.attachments()){
     String type=IntakeFileTypes.contentType(a.filename());
     if(type==null||a.content().length==0||a.content().length>IntakeFileTypes.MAX_BYTES)continue;
     var origin=new IntakeOrigin(IntakeOrigin.EMAIL,mail.messageId()==null?"mail-"+mail.id():mail.messageId(),null,config.getName(),mail.from(),mail.subject(),null,mail.sentAt(),null,"mail:"+config.getName());
     items.add(new IntakeItem(a.filename(),a.content(),type,origin));
    }
    if(items.isEmpty()){client.finish(mail,settings.processedFolder());withoutAttachment++;continue;}
    try{
     var receipts=sink.deliver(items);
     for(var r:receipts)if(r.created())created++;else duplicates++;
     client.finish(mail,settings.processedFolder());
    }catch(IllegalArgumentException refused){
     client.finish(mail,settings.rejectedFolder());failed++;
    }catch(IOException transientFailure){failed++;} // stays unread, tried again at the next run
   }
  }
  var notes=new ArrayList<String>();
  if(rejected>0)notes.add(rejected+" Mail(s) von nicht freigegebenen Absendern oder zu groß in „"+settings.rejectedFolder()+"“");
  if(withoutAttachment>0)notes.add(withoutAttachment+" Mail(s) ohne passenden Anhang");
  return new Result(created,duplicates,failed,0,notes.isEmpty()?null:String.join("; ",notes));
 }
}
