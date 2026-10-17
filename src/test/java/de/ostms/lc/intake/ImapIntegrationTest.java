package de.ostms.lc.intake;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.activation.DataHandler;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** The IMAP part against a real (test) IMAP server over TLS: unread mails, attachments, marking as read, moving to the folders. */
class ImapIntegrationTest {
 @RegisterExtension static GreenMailExtension greenMail=new GreenMailExtension(ServerSetupTest.IMAPS).withPerMethodLifecycle(true);
 static final String KEY="0123456789abcdef0123456789abcdef";
 final IntakeCipher cipher=new IntakeCipher(KEY);
 // the test server has a self-signed certificate: trust it for this test only (production keeps certificate and host name checks)
 final JakartaMailboxConnector connector=new JakartaMailboxConnector(p->{p.put("mail.imaps.ssl.trust","*");p.put("mail.imaps.ssl.checkserveridentity","false");});

 MimeMessage mail(String from,String subject,String filename,byte[] content,String type)throws Exception{
  var m=new MimeMessage(Session.getInstance(new Properties()));m.setFrom(new InternetAddress(from));m.setRecipients(jakarta.mail.Message.RecipientType.TO,"poststelle@lc.example");m.setSubject(subject);m.setSentDate(new Date());
  var multi=new MimeMultipart();var text=new MimeBodyPart();text.setText("Anbei");multi.addBodyPart(text);
  if(filename!=null){var part=new MimeBodyPart();part.setDataHandler(new DataHandler(new ByteArrayDataSource(content,type)));part.setFileName(filename);part.setDisposition("attachment");multi.addBodyPart(part);}
  m.setContent(multi);m.saveChanges();m.setHeader("Message-ID","<"+UUID.randomUUID()+"@kunde.example>");return m;
 }
 IntakeChannelConfig config(String... senders)throws Exception{
  var c=new IntakeChannelConfig("EMAIL","postfach",true,2,"admin");
  c.setSettings(new MailboxSettings("localhost",ServerSetupTest.IMAPS.getPort(),"SSL","poststelle@lc.example",null,null,null,List.of(senders)).toJson());
  c.setSecret(cipher.encrypt(c.getId().toString(),"pw"));return c;
 }
 MailIntakeChannel channel(){return new MailIntakeChannel(connector,cipher,new MailHostPolicy("localhost"));}
 List<String> messagesIn(String folder)throws Exception{
  var user=greenMail.getManagers().getUserManager().getUser("poststelle@lc.example");
  try(var store=greenMail.getImaps().createStore()){
   store.connect("poststelle@lc.example","pw");var f=store.getFolder(folder);if(!f.exists())return null;f.open(Folder.READ_ONLY);
   var out=new ArrayList<String>();for(var m:f.getMessages())out.add(m.getSubject()+(m.isSet(Flags.Flag.SEEN)?" [gelesen]":" [ungelesen]"));return out;
  }
 }
 IntakeChannel.IntakeSink sink(List<IntakeItem> delivered,boolean fail){return items->{if(fail)throw new java.io.IOException("Datenbank nicht erreichbar");delivered.addAll(items);return items.stream().map(i->new IntakeReceipt(i.filename(),IntakeReceipt.CREATED,UUID.randomUUID(),"x",null,null,true,null)).toList();};}

 @Test void attachmentsOfAllowedSendersAreDeliveredAndTheMailsMovedOthersAreRejected()throws Exception{
  greenMail.setUser("poststelle@lc.example","poststelle@lc.example","pw");
  var user=greenMail.getManagers().getUserManager().getUser("poststelle@lc.example");
  user.deliver(mail("einkauf@kunde.example","Rechnung 1","rechnung.pdf","PDFINHALT".getBytes(),"application/pdf"));
  user.deliver(mail("spam@fremd.example","Gewinn","x.pdf","X".getBytes(),"application/pdf"));
  user.deliver(mail("einkauf@kunde.example","Nur Text",null,null,null));
  user.deliver(mail("einkauf@kunde.example","Wort","brief.docx","W".getBytes(),"application/msword"));
  var delivered=new ArrayList<IntakeItem>();
  var result=channel().poll(config("@kunde.example"),sink(delivered,false));
  assertThat(delivered).extracting(IntakeItem::filename).containsExactly("rechnung.pdf");
  var item=delivered.get(0);assertThat(item.origin().sender()).isEqualTo("einkauf@kunde.example");assertThat(item.origin().subject()).isEqualTo("Rechnung 1");assertThat(item.content()).isEqualTo("PDFINHALT".getBytes());
  assertThat(result.created()).isEqualTo(1);assertThat(result.note()).contains("1 Mail(s) von nicht freigegebenen");
  assertThat(messagesIn("INBOX")).isEmpty();
  assertThat(messagesIn("LC-verarbeitet")).containsExactlyInAnyOrder("Rechnung 1 [gelesen]","Nur Text [gelesen]","Wort [gelesen]");
  assertThat(messagesIn("LC-abgelehnt")).containsExactly("Gewinn [gelesen]");
 }
 @Test void aTransientFailureLeavesTheMailUnreadInTheInboxAndTheNextRunDeliversIt()throws Exception{
  greenMail.setUser("poststelle@lc.example","poststelle@lc.example","pw");
  greenMail.getManagers().getUserManager().getUser("poststelle@lc.example").deliver(mail("a@kunde.example","Wiederholen","r.pdf","P".getBytes(),"application/pdf"));
  var failed=channel().poll(config("@kunde.example"),sink(new ArrayList<>(),true));
  assertThat(failed.failed()).isEqualTo(1);assertThat(messagesIn("INBOX")).containsExactly("Wiederholen [ungelesen]");
  var delivered=new ArrayList<IntakeItem>();var again=channel().poll(config("@kunde.example"),sink(delivered,false));
  assertThat(again.created()).isEqualTo(1);assertThat(messagesIn("INBOX")).isEmpty();assertThat(messagesIn("LC-verarbeitet")).containsExactly("Wiederholen [gelesen]");
 }
 @Test void theConnectionTestCountsUnreadMailsWithoutChangingAnything()throws Exception{
  greenMail.setUser("poststelle@lc.example","poststelle@lc.example","pw");
  var user=greenMail.getManagers().getUserManager().getUser("poststelle@lc.example");
  user.deliver(mail("a@kunde.example","Eins","1.pdf","P".getBytes(),"application/pdf"));user.deliver(mail("a@kunde.example","Zwei","2.pdf","P".getBytes(),"application/pdf"));
  assertThat(channel().test(config("@kunde.example"))).isEqualTo(2);
  assertThat(messagesIn("INBOX")).containsExactly("Eins [ungelesen]","Zwei [ungelesen]");
 }
 @Test void aWrongPasswordMissingFolderOrUnreachableServerGivesAClearError()throws Exception{
  greenMail.setUser("poststelle@lc.example","poststelle@lc.example","richtig");
  assertThatThrownBy(()->channel().test(config("@kunde.example"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("Verbindung zum Postfach fehlgeschlagen");
  greenMail.setUser("poststelle@lc.example","poststelle@lc.example","pw");
  var c=config("@kunde.example");c.setSettings(new MailboxSettings("localhost",ServerSetupTest.IMAPS.getPort(),"SSL","poststelle@lc.example","GIBT-ES-NICHT",null,null,List.of("@kunde.example")).toJson());
  assertThatThrownBy(()->channel().test(c)).hasMessageContaining("existiert nicht");
  var down=config("@kunde.example");down.setSettings(new MailboxSettings("localhost",1,"SSL","u",null,null,null,List.of("@kunde.example")).toJson());
  assertThatThrownBy(()->channel().test(down)).isInstanceOf(java.io.IOException.class);
 }
}
