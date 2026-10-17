package de.ostms.lc.intake;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

/** IMAP with Jakarta Mail: always encrypted (IMAPS or STARTTLS), certificate and host name checked, mails read without marking them. */
@Component
public class JakartaMailboxConnector implements MailboxConnector {
 static final long MAX_MESSAGE_BYTES=40L*1024*1024;

 @Override public MailboxClient open(MailboxSettings settings,String password)throws IOException{
  boolean ssl=MailboxSettings.SSL.equals(settings.security());String protocol=ssl?"imaps":"imap";
  var p=new Properties();
  p.put("mail."+protocol+".host",settings.host());p.put("mail."+protocol+".port",String.valueOf(settings.port()));
  p.put("mail."+protocol+".connectiontimeout","10000");p.put("mail."+protocol+".timeout","30000");p.put("mail."+protocol+".writetimeout","30000");
  p.put("mail."+protocol+".peek","true");p.put("mail."+protocol+".ssl.checkserveridentity","true");
  if(ssl)p.put("mail.imaps.ssl.enable","true");else{p.put("mail.imap.starttls.enable","true");p.put("mail.imap.starttls.required","true");}
  try{
   Store store=Session.getInstance(p).getStore(protocol);
   store.connect(settings.host(),settings.port(),settings.username(),password);
   Folder folder=store.getFolder(settings.folder());
   if(!folder.exists()){store.close();throw new IOException("Der Postfach-Ordner „"+settings.folder()+"“ existiert nicht.");}
   folder.open(Folder.READ_WRITE);
   return new Client(store,folder);
  }catch(MessagingException failure){throw new IOException("Verbindung zum Postfach fehlgeschlagen: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage()),failure);}
 }

 private static final class Client implements MailboxClient {
  private final Store store;private final Folder folder;private final Map<String,Message> open=new HashMap<>();
  Client(Store store,Folder folder){this.store=store;this.folder=folder;}
  @Override public List<MailMessage> unseen(int max)throws IOException{
   try{
    Message[] found=folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN),false));
    var out=new ArrayList<MailMessage>();
    for(int i=0;i<found.length&&out.size()<max;i++){
     Message m=found[i];String id=String.valueOf(m.getMessageNumber());
     if(m.getSize()>MAX_MESSAGE_BYTES){open.put(id,m);out.add(new MailMessage(id,null,null,null,null,List.of(),true));continue;}
     var parsed=MailMessageParser.parse(id,new MimeMessage((MimeMessage)m));
     open.put(id,m);out.add(parsed);
    }
    return out;
   }catch(MessagingException failure){throw new IOException("Das Postfach konnte nicht gelesen werden: "+failure.getMessage(),failure);}
  }
  @Override public void finish(MailMessage message,String target)throws IOException{
   try{
    Message m=open.get(message.id());if(m==null)throw new IOException("Mail nicht mehr geöffnet.");
    m.setFlag(Flags.Flag.SEEN,true);
    Folder destination=store.getFolder(target);
    if(!destination.exists())destination.create(Folder.HOLDS_MESSAGES);
    folder.copyMessages(new Message[]{m},destination);
    m.setFlag(Flags.Flag.DELETED,true);
    folder.expunge();
   }catch(MessagingException failure){throw new IOException("Die Mail konnte nicht verschoben werden: "+failure.getMessage(),failure);}
  }
  @Override public void close(){
   try{if(folder.isOpen())folder.close(false);}catch(MessagingException ignored){}
   try{store.close();}catch(MessagingException ignored){}
  }
 }
}
