package de.ostms.lc.document.api;

import de.ostms.lc.document.repository.*;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.rulepack.RuleFacts;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController
public class DocumentDuplicatesController {
 private final DocumentInboxRepository inbox;private final LcDocumentRepository documents;private final LetterOfCreditRepository lcs;
 public DocumentDuplicatesController(DocumentInboxRepository inbox,LcDocumentRepository documents,LetterOfCreditRepository lcs){this.inbox=inbox;this.documents=documents;this.lcs=lcs;}
 public record Member(UUID id,String filename,Integer copyNumber){}
 public record Group(String sha256,List<Member> documents){}
 public record Report(int checked,boolean limited,List<Group> groups){}
 @GetMapping("/api/inbox/duplicates") @Transactional(readOnly=true)
 public Report inbox(){
  var rows=inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN");
  return inspect(rows.stream().map(row->new Input(new Member(row.getId(),row.getOriginalFilename(),row.getCopyNumber()),row.getContent())).toList(),rows.size()==100);
 }
 @GetMapping("/api/lcs/{lcId}/documents/duplicates") @Transactional(readOnly=true)
 public Report documents(@PathVariable UUID lcId){
  if(!lcs.existsById(lcId))throw new NoSuchElementException();
  var rows=documents.findByLetterOfCreditIdOrderByUploadedAtDesc(lcId);
  return inspect(rows.stream().limit(100).map(row->new Input(new Member(row.getId(),row.getOriginalFilename(),row.getCopyNumber()),row.getContent())).toList(),rows.size()>100);
 }
 public record Input(Member member,byte[] bytes){}
 public static Report inspect(List<Input> inputs,boolean limited){
  var grouped=new LinkedHashMap<String,List<Member>>();long bytes=0;int checked=0;
  for(var input:inputs){
   if(input.bytes()==null)continue;
   bytes+=input.bytes().length;if(bytes>100L*1024*1024){limited=true;break;}
   grouped.computeIfAbsent(RuleFacts.contentFingerprint(input.bytes()),key->new ArrayList<>()).add(input.member());checked++;
  }
  return new Report(checked,limited,grouped.entrySet().stream().filter(entry->entry.getValue().size()>1).map(entry->new Group(entry.getKey(),List.copyOf(entry.getValue()))).toList());
 }
}
