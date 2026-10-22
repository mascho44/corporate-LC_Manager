package de.ostms.lc.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

/** Payments of a file. Reading is open to every signed-in user; entering and deleting needs the right to edit files (see SecurityConfig). */
@RestController @RequestMapping("/api/lcs/{lcId}/payments")
public class PaymentController {
 private final PaymentService service;
 public PaymentController(PaymentService service){this.service=service;}
 @GetMapping public PaymentService.Payments list(@PathVariable UUID lcId){return service.forLc(lcId,LocalDate.now());}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public PaymentService.View record(@PathVariable UUID lcId,@RequestBody PaymentService.Request request,Authentication auth){return service.record(lcId,request,"MANUAL",auth);}
 @DeleteMapping("/{paymentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID lcId,@PathVariable UUID paymentId,Authentication auth){service.delete(lcId,paymentId,auth);}
}
