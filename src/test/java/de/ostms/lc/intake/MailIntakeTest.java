package de.ostms.lc.intake;

import jakarta.activation.DataHandler;
import jakarta.mail.Session;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MailIntakeTest {
 static final String KEY="0123456789abcdef0123456789abcdef";
 final IntakeCipher cipher=new IntakeCipher(KEY);

 // ---- settings and cipher
 @Test void settingsAreValidatedNormalisedAndAllowSendersByAddressOrDomain(){
  var s=new MailboxSettings(" mail.kunde.example ",993,"ssl","poststelle@kunde.example",null,null,null,List.of("Einkauf@Kunde.example","@partner.example","einkauf@kunde.example"," "));
  assertThat(s.host()).isEqualTo("mail.kunde.example");assertThat(s.security()).isEqualTo("SSL");assertThat(s.folder()).isEqualTo("INBOX");assertThat(s.processedFolder()).isEqualTo("LC-verarbeitet");assertThat(s.rejectedFolder()).isEqualTo("LC-abgelehnt");
  assertThat(s.allowedSenders()).containsExactly("einkauf@kunde.example","@partner.example");
  assertThat(s.allows("EINKAUF@kunde.example")).isTrue();assertThat(s.allows("jemand@partner.example")).isTrue();assertThat(s.allows("jemand@fremd.example")).isFalse();assertThat(s.allows(null)).isFalse();assertThat(s.allows("kein-at")).isFalse();
  assertThat(MailboxSettings.fromJson(s.toJson())).isEqualTo(s);
 }
 @Test void unsafeOrWrongSettingsAreRefused(){
  assertThatThrownBy(()->new MailboxSettings("a b",993,"SSL","u",null,null,null,List.of())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new MailboxSettings("x.example",0,"SSL","u",null,null,null,List.of())).hasMessageContaining("Port");
  assertThatThrownBy(()->new MailboxSettings("x.example",143,"NONE","u",null,null,null,List.of())).hasMessageContaining("verschlüsselt");
  assertThatThrownBy(()->new MailboxSettings("x.example",993,"SSL"," ",null,null,null,List.of())).hasMessageContaining("Benutzername");
  assertThatThrownBy(()->new MailboxSettings("x.example",993,"SSL","u","../x",null,null,List.of())).hasMessageContaining("Ordner");
  assertThatThrownBy(()->new MailboxSettings("x.example",993,"SSL","u",null,null,null,List.of("kein absender"))).hasMessageContaining("Ungültiger Absender");
  assertThatThrownBy(()->new MailboxSettings("x..example",993,"SSL","u",null,null,null,List.of())).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void thePasswordIsSealedPerChannelAndNeedsTheKey(){
  byte[] sealed=cipher.encrypt("kanal-1","geheim");
  assertThat(new String(sealed)).doesNotContain("geheim");assertThat(cipher.decrypt("kanal-1",sealed)).isEqualTo("geheim");
  assertThatThrownBy(()->cipher.decrypt("kanal-2",sealed)).isInstanceOf(IllegalStateException.class);
  var none=new IntakeCipher("");assertThat(none.configured()).isFalse();assertThatThrownBy(()->none.encrypt("k","x")).hasMessageContaining("INTAKE_ENCRYPTION_KEY");
  assertThatThrownBy(()->new IntakeCipher("anderer-schluessel-anderer-schluessel").decrypt("kanal-1",sealed)).isInstanceOf(IllegalStateException.class);
 }
 @Test void internalHostsAreRefusedUnlessAllowListed(){
  var policy=new MailHostPolicy("");
  assertThatThrownBy(()->policy.check("localhost")).hasMessageContaining("intern");
  assertThatThrownBy(()->policy.check("127.0.0.1")).hasMessageContaining("intern");
  assertThatThrownBy(()->policy.check("192.168.1.10")).hasMessageContaining("intern");
  assertThatCode(()->new MailHostPolicy("localhost, mail.firma.intern").check("localhost")).doesNotThrowAnyException();
 }

 // ---- parser
 MimeMessage mail(String from,String subject,MimeBodyPart... parts)throws Exception{
  var m=new MimeMessage(Session.getInstance(new Properties()));m.setFrom(new InternetAddress(from,"Absender"));m.setSubject(subject);m.setSentDate(new Date());
  var multi=new MimeMultipart();var text=new MimeBodyPart();text.setText("Anbei die Unterlagen");multi.addBodyPart(text);for(var p:parts)multi.addBodyPart(p);
  m.setContent(multi);m.saveChanges();m.setHeader("Message-ID","<abc123@kunde.example>");return m;
 }
 MimeBodyPart file(String name,String type,int size,String disposition)throws Exception{
  var p=new MimeBodyPart();p.setDataHandler(new DataHandler(new ByteArrayDataSource(new byte[size],type)));p.setFileName(name);p.setDisposition(disposition);return p;
 }
 @Test void attachmentsAreReadAndSmallInlineLogosAreSkipped()throws Exception{
  var parsed=MailMessageParser.parse("1",mail("einkauf@kunde.example","Dokumente zu LC 4711",file("rechnung.pdf","application/pdf",3000,"attachment"),file("logo.png","image/png",2000,"inline"),file("foto.jpg","image/jpeg",30000,"inline")));
  assertThat(parsed.from()).isEqualTo("einkauf@kunde.example");assertThat(parsed.subject()).isEqualTo("Dokumente zu LC 4711");assertThat(parsed.messageId()).isEqualTo("<abc123@kunde.example>");assertThat(parsed.sentAt()).isNotNull();
  assertThat(parsed.attachments()).extracting(MailMessage.Attachment::filename).containsExactly("rechnung.pdf","foto.jpg");
 }

 // ---- channel with a fake mailbox
 static class FakeClient implements MailboxClient {
  final List<MailMessage> mails;final Map<String,String> moved=new LinkedHashMap<>();boolean closed;
  FakeClient(List<MailMessage> mails){this.mails=mails;}
  public List<MailMessage> unseen(int max){return mails.stream().limit(max).toList();}
  public void finish(MailMessage m,String folder){moved.put(m.id(),folder);}
  public void close(){closed=true;}
 }
 MailMessage message(String id,String from,String... files){
  var attachments=new ArrayList<MailMessage.Attachment>();for(String f:files)attachments.add(new MailMessage.Attachment(f,null,("INHALT "+f+id).getBytes()));
  return new MailMessage(id,"<"+id+"@x>",from,"Betreff "+id,java.time.LocalDateTime.of(2026,10,17,9,0),attachments,false);
 }
 IntakeChannelConfig config()throws Exception{
  var c=new IntakeChannelConfig("EMAIL","postfach",true,2,"admin");
  c.setSettings(new MailboxSettings("mail.kunde.example",993,"SSL","u",null,null,null,List.of("@kunde.example")).toJson());c.setSecret(cipher.encrypt(c.getId().toString(),"pw"));return c;
 }
 IntakeChannel.IntakeSink sink(List<IntakeItem> delivered,String behaviour){return items->{
  if("refuse".equals(behaviour))throw new IllegalArgumentException("Prüfwert falsch");if("io".equals(behaviour))throw new IOException("Datenbank nicht erreichbar");
  delivered.addAll(items);return items.stream().map(i->new IntakeReceipt(i.filename(),"duplicate".equals(behaviour)?IntakeReceipt.DUPLICATE:IntakeReceipt.CREATED,UUID.randomUUID(),"x",null,null,true,null)).toList();};}
 MailIntakeChannel channel(FakeClient client){return new MailIntakeChannel((settings,password)->{assertThat(password).isEqualTo("pw");return client;},cipher,new MailHostPolicy("mail.kunde.example"));}

 @Test void mailsFromAllowedSendersAreDeliveredAndMovedOthersAreRejectedWithoutReading()throws Exception{
  var big=new MailMessage("5",null,null,null,null,List.of(),true);
  var client=new FakeClient(List.of(message("1","einkauf@kunde.example","rechnung.pdf","notiz.docx","scan.TIF"),message("2","spam@fremd.example","x.pdf"),message("3","a@kunde.example"),big,message("4","b@kunde.example","nur.docx")));
  var delivered=new ArrayList<IntakeItem>();
  var result=channel(client).poll(config(),sink(delivered,"ok"));
  assertThat(delivered).extracting(IntakeItem::filename).containsExactly("rechnung.pdf","scan.TIF");
  var item=delivered.get(0);assertThat(item.contentType()).isEqualTo("application/pdf");assertThat(item.origin().channel()).isEqualTo("EMAIL");assertThat(item.origin().sender()).isEqualTo("einkauf@kunde.example");assertThat(item.origin().subject()).isEqualTo("Betreff 1");assertThat(item.origin().reference()).isEqualTo("<1@x>");assertThat(item.origin().actor()).isEqualTo("mail:postfach");
  assertThat(client.moved).containsEntry("1","LC-verarbeitet").containsEntry("2","LC-abgelehnt").containsEntry("3","LC-verarbeitet").containsEntry("4","LC-verarbeitet").containsEntry("5","LC-abgelehnt");
  assertThat(result.created()).isEqualTo(2);assertThat(result.failed()).isZero();assertThat(result.note()).contains("2 Mail(s)").contains("ohne passenden Anhang");assertThat(client.closed).isTrue();
 }
 @Test void aRefusedDeliveryMovesTheMailAwayAndATransientFailureLeavesItUnread()throws Exception{
  var refused=new FakeClient(List.of(message("1","a@kunde.example","x.pdf")));
  var r1=channel(refused).poll(config(),sink(new ArrayList<>(),"refuse"));
  assertThat(r1.failed()).isEqualTo(1);assertThat(refused.moved).containsEntry("1","LC-abgelehnt");
  var transientFail=new FakeClient(List.of(message("1","a@kunde.example","x.pdf")));
  var r2=channel(transientFail).poll(config(),sink(new ArrayList<>(),"io"));
  assertThat(r2.failed()).isEqualTo(1);assertThat(transientFail.moved).isEmpty();
  var duplicate=new FakeClient(List.of(message("1","a@kunde.example","x.pdf")));
  var r3=channel(duplicate).poll(config(),sink(new ArrayList<>(),"duplicate"));
  assertThat(r3.duplicates()).isEqualTo(1);assertThat(duplicate.moved).containsEntry("1","LC-verarbeitet");
 }
 @Test void anIncompleteOrInternalMailboxIsNotOpened()throws Exception{
  var c=new IntakeChannelConfig("EMAIL","postfach",true,2,"admin");
  var channel=channel(new FakeClient(List.of()));
  assertThatThrownBy(()->channel.poll(c,sink(new ArrayList<>(),"ok"))).isInstanceOf(java.io.IOException.class).hasMessageContaining("nicht vollständig");
  var internal=config();internal.setSettings(new MailboxSettings("localhost",993,"SSL","u",null,null,null,List.of("@kunde.example")).toJson());
  var strict=new MailIntakeChannel((s,p)->new FakeClient(List.of()),cipher,new MailHostPolicy(""));
  assertThatThrownBy(()->strict.poll(internal,sink(new ArrayList<>(),"ok"))).hasMessageContaining("intern");
 }
}
