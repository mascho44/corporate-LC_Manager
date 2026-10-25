package de.ostms.lc.intake;

import java.time.LocalDateTime;
import java.util.List;

/** A mail as the channel needs it: who sent it, what it is about and the attachments that are candidates for the inbox. tooLarge marks a mail that was not read. */
public record MailMessage(String id,String messageId,String from,String subject,LocalDateTime sentAt,List<Attachment> attachments,boolean tooLarge){
 public record Attachment(String filename,String contentType,byte[] content){}
}
