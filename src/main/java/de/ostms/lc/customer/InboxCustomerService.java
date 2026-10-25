package de.ostms.lc.customer;

import de.ostms.lc.capture.InboxCaptureInfoRepository;
import de.ostms.lc.document.repository.DocumentInboxRepository;
import de.ostms.lc.lc.domain.LetterOfCredit;
import de.ostms.lc.lc.domain.LetterOfCreditStatus;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** For inbox documents that are submission letters: what the letter says, which customer fits, and the customer a person has confirmed or chosen. */
@Service
public class InboxCustomerService {
 public record Assigned(UUID customerId,String name,String method){}
 /** An LC file of the customer; `byLetter` marks the one the letter itself names. */
 public record LcOption(UUID id,String reference,String status,boolean byLetter,boolean linked){}
 /** `via` is LETTER (read from the submission letter) or BATCH (taken from the submission letter of the same delivery batch). */
 public record Entry(SubmissionLetterReader.Fields fields,List<CustomerMatcher.Candidate> candidates,boolean clear,Assigned assigned,List<LcOption> lcs,String via){}
 private static final int MAX_CANDIDATES=3;

 private final DocumentInboxRepository inbox;private final CustomerRepository customers;private final InboxCustomerAssignmentRepository assignments;private final LetterOfCreditRepository lettersOfCredit;private final InboxCaptureInfoRepository captureInfos;
 public InboxCustomerService(DocumentInboxRepository inbox,CustomerRepository customers,InboxCustomerAssignmentRepository assignments,LetterOfCreditRepository lettersOfCredit,InboxCaptureInfoRepository captureInfos){this.inbox=inbox;this.customers=customers;this.assignments=assignments;this.lettersOfCredit=lettersOfCredit;this.captureInfos=captureInfos;}

 private static String key(String reference){return reference==null?"":reference.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}

 /** The active LC files of a customer, the one named by the letter first. */
 private List<LcOption> lcsOf(Customer customer,List<LetterOfCredit> all,String letterReference){
  if(customer==null)return List.of();
  List<LcOption> out=new ArrayList<>();
  for(LetterOfCredit lc:all){
   if(lc.getStatus()==LetterOfCreditStatus.CLOSED||lc.getStatus()==LetterOfCreditStatus.EXPIRED)continue;
   boolean named=!key(letterReference).isEmpty()&&key(letterReference).equals(key(lc.getReference()));
   boolean linked=customer.getId().equals(lc.getCustomerId());
   // An explicit link wins over the name comparison: a file linked to another customer is never offered here.
   if(named||linked||(lc.getCustomerId()==null&&LcCustomerMatcher.matches(customer,lc)))out.add(new LcOption(lc.getId(),lc.getReference(),lc.getStatus()==null?null:lc.getStatus().name(),named,linked));
  }
  out.sort(Comparator.comparing(LcOption::byLetter).reversed().thenComparing(LcOption::linked,Comparator.reverseOrder()).thenComparing(LcOption::reference,Comparator.nullsLast(Comparator.naturalOrder())));
  return out.size()>10?List.copyOf(out.subList(0,10)):out;
 }

 /**
  * Entries by inbox item id. Submission letters carry what the letter says, the customer that fits and the customer a person confirmed, plus the LC files of that
  * customer (the one the letter names first). Other documents of the same delivery batch get the customer of the batch's confirmed letter as a suggestion.
  * Nothing is written here.
  */
 @Transactional(readOnly=true) public Map<UUID,Entry> overview(){
  var all=customers.findAll();var byId=new HashMap<UUID,Customer>();all.forEach(c->byId.put(c.getId(),c));
  var lcsAll=lettersOfCredit.findAll();
  var stored=new HashMap<UUID,InboxCustomerAssignment>();assignments.findAll().forEach(a->stored.put(a.getInboxItemId(),a));
  var batchOf=new HashMap<UUID,String>();captureInfos.findAll().forEach(i->{if(i.getBatchId()!=null&&!i.getBatchId().isBlank())batchOf.put(i.getInboxItemId(),i.getBatchId());});
  var customerOfBatch=new HashMap<String,UUID>();
  var items=inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN");
  Map<UUID,Entry> out=new LinkedHashMap<>();
  for(var item:items){
   boolean submission=SubmissionLetterReader.looksLikeSubmission(item.getOriginalFilename(),item.getExtractedText());
   var chosen=stored.get(item.getId());
   if(!submission&&chosen==null)continue;
   var fields=SubmissionLetterReader.read(item.getExtractedText());
   var match=CustomerMatcher.suggest(fields,all);
   Assigned assigned=null;
   if(chosen!=null&&byId.containsKey(chosen.getCustomerId()))assigned=new Assigned(chosen.getCustomerId(),byId.get(chosen.getCustomerId()).getName(),chosen.getMethod());
   Customer steering=assigned!=null?byId.get(assigned.customerId()):match.clear()?byId.get(match.best().customerId()):null;
   if(assigned!=null&&submission&&batchOf.containsKey(item.getId()))customerOfBatch.putIfAbsent(batchOf.get(item.getId()),assigned.customerId());
   out.put(item.getId(),new Entry(fields,match.candidates().stream().limit(MAX_CANDIDATES).toList(),match.clear(),assigned,lcsOf(steering,lcsAll,fields.lcReference()),"LETTER"));
  }
  for(var item:items){
   if(out.containsKey(item.getId()))continue;
   String batch=batchOf.get(item.getId());var customerId=batch==null?null:customerOfBatch.get(batch);var customer=customerId==null?null:byId.get(customerId);
   if(customer==null)continue;
   var candidate=new CustomerMatcher.Candidate(customer.getId(),customer.getName(),"Stapel "+batch,0);
   out.put(item.getId(),new Entry(new SubmissionLetterReader.Fields(null,null,null,null,null),List.of(candidate),true,null,lcsOf(customer,lcsAll,null),"BATCH"));
  }
  return out;
 }

 @Transactional public Assigned assign(UUID inboxItemId,UUID customerId,String user){
  var item=inbox.findById(inboxItemId).orElseThrow(()->new NoSuchElementException("Dokument nicht gefunden."));
  var customer=customers.findById(customerId).orElseThrow(()->new NoSuchElementException("Kunde nicht gefunden."));
  var match=CustomerMatcher.suggest(SubmissionLetterReader.read(item.getExtractedText()),customers.findAll());
  String method=match.clear()&&match.best().customerId().equals(customerId)?"SUGGESTION":"MANUAL";
  var row=assignments.forItem(inboxItemId).orElseGet(()->new InboxCustomerAssignment(inboxItemId));
  row.assign(customerId,method,user);assignments.save(row);
  return new Assigned(customerId,customer.getName(),method);
 }

 @Transactional public void unassign(UUID inboxItemId){assignments.forItem(inboxItemId).ifPresent(assignments::delete);}
}
