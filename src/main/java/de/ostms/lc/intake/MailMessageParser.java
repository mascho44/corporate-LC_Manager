package de.ostms.lc.intake;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Reads sender, subject, Message-ID and attachments from a mail. Inline images below 20 KB (logos in signatures) are not attachments. */
final class MailMessageParser {
 private MailMessageParser(){}
 static final int INLINE_MIN_BYTES=20*1024,MAX_DEPTH=8;

 static MailMessage parse(String id,MimeMessage message)throws MessagingException,IOException{
  String from=null;
  Address[] senders=message.getFrom();
  if(senders!=null&&senders.length>0&&senders[0] instanceof InternetAddress a)from=a.getAddress();
  String[] ids=message.getHeader("Message-ID");
  String subject=message.getSubject();
  Date sent=message.getSentDate()!=null?message.getSentDate():message.getReceivedDate();
  var attachments=new ArrayList<MailMessage.Attachment>();
  collect(message,attachments,0);
  return new MailMessage(id,ids==null||ids.length==0?null:ids[0],from,subject,sent==null?null:LocalDateTime.ofInstant(sent.toInstant(),ZoneId.systemDefault()),List.copyOf(attachments),false);
 }
 private static void collect(Part part,List<MailMessage.Attachment> out,int depth)throws MessagingException,IOException{
  if(depth>MAX_DEPTH)return;
  if(part.isMimeType("multipart/*")){
   var multipart=(Multipart)part.getContent();
   for(int i=0;i<multipart.getCount();i++)collect(multipart.getBodyPart(i),out,depth+1);
   return;
  }
  if(part.isMimeType("message/rfc822")){if(part.getContent() instanceof Part nested)collect(nested,out,depth+1);return;}
  String name=part.getFileName();
  if(name==null||name.isBlank())return;
  try{name=MimeUtility.decodeText(name);}catch(java.io.UnsupportedEncodingException ignored){}
  boolean inline=Part.INLINE.equalsIgnoreCase(part.getDisposition());
  byte[] content;
  try(var in=part.getInputStream()){content=in.readNBytes((int)IntakeFileTypes.MAX_BYTES+1);}
  if(content.length==0)return;
  if(inline&&content.length<INLINE_MIN_BYTES&&part.isMimeType("image/*"))return;
  String type=part.getContentType()==null?null:part.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
  out.add(new MailMessage.Attachment(name.replace('\\','/').substring(name.replace('\\','/').lastIndexOf('/')+1),type,content));
 }
}
