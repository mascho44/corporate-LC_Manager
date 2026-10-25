package de.ostms.lc.intake;

import de.ostms.lc.capture.InboxCaptureInfo;
import de.ostms.lc.capture.InboxCaptureInfoRepository;
import de.ostms.lc.document.service.DocumentInboxService;
import de.ostms.lc.document.service.PdfPagePreviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;

/**
 * The one way into the document inbox for everything that is not typed in by a person: scan station, mailbox, watched folder. A file that arrived before
 * (same checksum in this tenant) is not stored a second time, so a channel may simply retry; the declared page count of a scanner is compared with the real
 * one; where a file came from is kept next to the inbox entry. Channels only deliver {@link IntakeItem}s and act on the {@link IntakeReceipt}s.
 */
@Service
public class InboxIntakeService {
 private final DocumentInboxService inbox;private final InboxCaptureInfoRepository infos;private final PdfPagePreviewService pdf;
 public InboxIntakeService(DocumentInboxService inbox,InboxCaptureInfoRepository infos,PdfPagePreviewService pdf){this.inbox=inbox;this.infos=infos;this.pdf=pdf;}

 public static String sha256(byte[] bytes){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }

 /** Stores the files that are new; one receipt per file in the same order. A file that cannot be stored stops the batch (the transaction rolls back). */
 @Transactional
 public List<IntakeReceipt> accept(List<IntakeItem> items)throws IOException{
  List<IntakeReceipt> receipts=new ArrayList<>();
  for(IntakeItem item:items){
   String name=item.filename();byte[] bytes=item.content();String sha=sha256(bytes);var origin=item.origin();
   if(origin.sha256()!=null&&!origin.sha256().strip().equalsIgnoreCase(sha))throw new IllegalArgumentException("Der Prüfwert (SHA-256) stimmt nicht mit der Datei überein; bitte erneut senden.");
   var earlier=infos.bySha(sha).orElse(null);
   if(earlier!=null){
    receipts.add(new IntakeReceipt(name,IntakeReceipt.DUPLICATE,earlier.getInboxItemId(),sha,earlier.getDeclaredPages(),earlier.getActualPages(),earlier.pageCountMatches(),"Diese Datei ist schon eingegangen; es wurde nichts neu angelegt."));
    continue;
   }
   var created=inbox.receive(List.of(new BytesMultipartFile(name,item.contentType(),bytes)),origin.actor());
   if(created.size()!=1)throw new IllegalArgumentException("Aus dieser Datei entstand nicht genau ein Eintrag; bitte als einzelne Datei senden.");
   Integer actual=null;
   if(name.toLowerCase(Locale.ROOT).endsWith(".pdf")){try{actual=pdf.pageCount(bytes);}catch(Exception unreadable){actual=null;}}
   var info=infos.save(new InboxCaptureInfo(created.get(0).id(),origin.actor(),origin.batchId(),origin.device(),origin.declaredPages(),actual,origin.sentAt(),sha).origin(origin.channel(),origin.reference(),origin.sender(),origin.subject()));
   String note=info.pageCountMatches()?null:"Gemeldet "+info.getDeclaredPages()+" Seiten, erkannt "+info.getActualPages()+". Bitte den Stapel prüfen.";
   receipts.add(new IntakeReceipt(name,IntakeReceipt.CREATED,created.get(0).id(),sha,info.getDeclaredPages(),info.getActualPages(),info.pageCountMatches(),note));
  }
  return receipts;
 }
}
