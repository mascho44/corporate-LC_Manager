package de.ostms.lc.lc.api;
import de.ostms.lc.lc.domain.*;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.lc.service.LcConditions;
import de.ostms.lc.audit.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/lcs/{id}/conditions")
public class LcConditionsController {
 private final LetterOfCreditRepository lcs;private final AuditService audit;
 public LcConditionsController(LetterOfCreditRepository lcs,AuditService audit){this.lcs=lcs;this.audit=audit;}
 public record Request(@NotNull @Size(max=11) Map<@NotNull LcCondition,@NotNull @Size(max=4000) String> conditions){}
 @GetMapping @Transactional(readOnly=true) public Map<LcCondition,String> read(@PathVariable UUID id){
  var lc=lcs.findById(id).orElseThrow();var result=new EnumMap<LcCondition,String>(LcCondition.class);
  for(var key:LcCondition.values())LcConditions.value(lc,key).ifPresent(value->result.put(key,value));return result;
 }
 @PutMapping @Transactional public Map<LcCondition,String> update(@PathVariable UUID id,@Valid @RequestBody Request request,Authentication auth){
  var lc=lcs.findById(id).orElseThrow();request.conditions().forEach((key,value)->lc.getConditions().put(key,value.strip()));
  audit.recordInTransaction(auth,"LC_CONDITIONS_UPDATED","LETTER_OF_CREDIT",id,"Formatneutrale Bedingungen geändert: "+request.conditions().keySet());return read(id);
 }
}
