package de.ostms.lc.charges;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ostms.lc.lc.repository.LetterOfCreditRepository;
import de.ostms.lc.audit.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController public class ChargeController {
 private final ChargeProfileRepository profiles;private final ChargeEstimateRepository estimates;private final LetterOfCreditRepository lcs;private final ObjectMapper json;private final AuditService audit;
 public ChargeController(ChargeProfileRepository profiles,ChargeEstimateRepository estimates,LetterOfCreditRepository lcs,ObjectMapper json,AuditService audit){this.profiles=profiles;this.estimates=estimates;this.lcs=lcs;this.json=json;this.audit=audit;}
 public record ProfileRequest(@NotBlank @Size(max=100) String name,@Size(max=255) String bankName,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotEmpty @Size(max=5) List<@NotNull @Valid ChargeRule> rules){}
 public record EstimateRequest(@NotNull UUID profileId,@NotEmpty @Size(max=5) Map<ChargeType,@NotNull @Min(0) @Max(10000) Integer> units){}
 @GetMapping("/api/charge-profiles") public List<ChargeProfile> profiles(){return profiles.findAll();}
 @PostMapping("/api/charge-profiles") @Transactional public ChargeProfile create(@Valid @RequestBody ProfileRequest request,Authentication auth)throws Exception{
  // Validate tariff consistency with an innocuous calculation before storing an immutable profile.
  ChargeCalculator.calculate(java.math.BigDecimal.ONE,request.currency(),request.rules(),Map.of(request.rules().get(0).type(),1));
  var profile=new ChargeProfile();profile.name=request.name().strip();profile.bankName=request.bankName();profile.currency=request.currency();profile.rulesJson=json.writeValueAsString(request.rules());profile.createdBy=auth.getName();profiles.save(profile);
  audit.recordInTransaction(auth,"LC_CHARGE_PROFILE_CREATED","CHARGE_PROFILE",profile.id,"Gebührenprofil angelegt: "+profile.name);return profile;
 }
 @GetMapping("/api/lcs/{id}/charges") public List<ChargeEstimate> history(@PathVariable UUID id){lcs.findById(id).orElseThrow();return estimates.findByLcIdOrderByCreatedAtDesc(id);}
 @PostMapping("/api/lcs/{id}/charges") @Transactional public ChargeEstimate estimate(@PathVariable UUID id,@Valid @RequestBody EstimateRequest request,Authentication auth)throws Exception{
  var lc=lcs.findById(id).orElseThrow();var profile=profiles.findById(request.profileId()).orElseThrow();
  if(!Objects.equals(lc.getCurrency(),profile.currency))throw new IllegalArgumentException("Profil- und LC-Währung müssen übereinstimmen. Keine automatische Umrechnung.");
  List<ChargeRule> rules=json.readValue(profile.rulesJson,new TypeReference<List<ChargeRule>>(){});
  var result=ChargeCalculator.calculate(lc.getAmount(),lc.getCurrency(),rules,request.units());
  var estimate=new ChargeEstimate();estimate.lcId=id;estimate.profileId=profile.id;estimate.profileSnapshot=json.writeValueAsString(profile);estimate.resultJson=json.writeValueAsString(result);estimate.createdBy=auth.getName();estimates.save(estimate);
  audit.recordInTransaction(auth,"LC_CHARGES_ESTIMATED","LETTER_OF_CREDIT",id,"Gebührenschätzung "+estimate.id+" · Profil "+profile.id+" · "+result.total()+" "+result.currency());return estimate;
 }
}
