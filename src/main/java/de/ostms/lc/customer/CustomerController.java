package de.ostms.lc.customer;

import de.ostms.lc.audit.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CustomerController {
 public record AssignRequest(UUID customerId){}
 private final CustomerService customers;private final InboxCustomerService inboxCustomers;private final AuditService audit;
 public CustomerController(CustomerService customers,InboxCustomerService inboxCustomers,AuditService audit){this.customers=customers;this.inboxCustomers=inboxCustomers;this.audit=audit;}

 @GetMapping("/api/customers") public List<CustomerService.View> list(){return customers.list();}

 @PostMapping("/api/customers") @ResponseStatus(HttpStatus.CREATED)
 public CustomerService.View create(@RequestBody CustomerService.Request request,Authentication authentication){
  var view=customers.create(request,authentication.getName());
  audit.record(authentication,"CUSTOMER_CREATED","CUSTOMER",view.id(),view.name()+(view.customerNumber()==null?"":" · "+view.customerNumber()));
  return view;
 }
 @PutMapping("/api/customers/{id}")
 public CustomerService.View update(@PathVariable UUID id,@RequestBody CustomerService.Request request,Authentication authentication){
  var view=customers.update(id,request,authentication.getName());
  audit.record(authentication,"CUSTOMER_CHANGED","CUSTOMER",id,view.name()+(view.customerNumber()==null?"":" · "+view.customerNumber()));
  return view;
 }
 @DeleteMapping("/api/customers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id,Authentication authentication){audit.record(authentication,"CUSTOMER_DELETED","CUSTOMER",id,customers.delete(id));}

 @GetMapping("/api/inbox-customers") public Map<UUID,InboxCustomerService.Entry> overview(){return inboxCustomers.overview();}

 @PutMapping("/api/inbox-customers/{itemId}")
 public InboxCustomerService.Assigned assign(@PathVariable UUID itemId,@RequestBody AssignRequest request,Authentication authentication){
  if(request==null||request.customerId()==null)throw new IllegalArgumentException("Bitte einen Kunden auswählen.");
  var assigned=inboxCustomers.assign(itemId,request.customerId(),authentication.getName());
  audit.record(authentication,"INBOX_CUSTOMER_ASSIGNED","DOCUMENT_INBOX",itemId,assigned.name()+" · "+assigned.method());
  return assigned;
 }
 @DeleteMapping("/api/inbox-customers/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void unassign(@PathVariable UUID itemId,Authentication authentication){inboxCustomers.unassign(itemId);audit.record(authentication,"INBOX_CUSTOMER_UNASSIGNED","DOCUMENT_INBOX",itemId,"Kundenzuordnung entfernt");}
}
