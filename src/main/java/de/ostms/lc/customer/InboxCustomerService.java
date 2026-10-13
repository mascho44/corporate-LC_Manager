package de.ostms.lc.customer;

import de.ostms.lc.document.repository.DocumentInboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** For inbox documents that are submission letters: what the letter says, which customer fits, and the customer a person has confirmed or chosen. */
@Service
public class InboxCustomerService {
 public record Assigned(UUID customerId,String name,String method){}
 public record Entry(SubmissionLetterReader.Fields fields,List<CustomerMatcher.Candidate> candidates,boolean clear,Assigned assigned){}
 private static final int MAX_CANDIDATES=3;

 private final DocumentInboxRepository inbox;private final CustomerRepository customers;private final InboxCustomerAssignmentRepository assignments;
 public InboxCustomerService(DocumentInboxRepository inbox,CustomerRepository customers,InboxCustomerAssignmentRepository assignments){this.inbox=inbox;this.customers=customers;this.assignments=assignments;}

 /** Entries by inbox item id; documents that are not submission letters have none. Nothing is written here. */
 @Transactional(readOnly=true) public Map<UUID,Entry> overview(){
  var all=customers.findAll();var byId=new HashMap<UUID,Customer>();all.forEach(c->byId.put(c.getId(),c));
  var stored=new HashMap<UUID,InboxCustomerAssignment>();assignments.findAll().forEach(a->stored.put(a.getInboxItemId(),a));
  Map<UUID,Entry> out=new LinkedHashMap<>();
  for(var item:inbox.findTop100ByStatusOrderByReceivedAtDesc("OPEN")){
   boolean submission=SubmissionLetterReader.looksLikeSubmission(item.getOriginalFilename(),item.getExtractedText());
   var chosen=stored.get(item.getId());
   if(!submission&&chosen==null)continue;
   var fields=SubmissionLetterReader.read(item.getExtractedText());
   var match=CustomerMatcher.suggest(fields,all);
   Assigned assigned=null;
   if(chosen!=null&&byId.containsKey(chosen.getCustomerId()))assigned=new Assigned(chosen.getCustomerId(),byId.get(chosen.getCustomerId()).getName(),chosen.getMethod());
   out.put(item.getId(),new Entry(fields,match.candidates().stream().limit(MAX_CANDIDATES).toList(),match.clear(),assigned));
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
