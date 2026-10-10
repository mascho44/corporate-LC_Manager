package de.ostms.lc.document.service;
import de.ostms.lc.document.domain.TenantScanProfile;
import de.ostms.lc.document.repository.TenantScanProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The scan profile of the current tenant; STANDARD until a tenant chooses another one. */
@Service
public class ScanProfileService {
 public record View(String current,java.util.List<ScanProfile> available,String changedBy,java.time.LocalDateTime changedAt){}
 private final TenantScanProfileRepository repo;
 public ScanProfileService(TenantScanProfileRepository r){repo=r;}

 @Transactional(readOnly=true) public ScanProfile current(){return repo.current().map(s->ScanProfile.byId(s.getProfile())).orElse(ScanProfile.STANDARD);}
 @Transactional(readOnly=true) public View view(){
  var setting=repo.current();
  return new View(setting.map(s->ScanProfile.byId(s.getProfile()).id()).orElse(ScanProfile.STANDARD.id()),java.util.List.copyOf(ScanProfile.all()),setting.map(TenantScanProfile::getChangedBy).orElse(null),setting.map(TenantScanProfile::getChangedAt).orElse(null));
 }
 /** Returns the profile that was active before, for the audit trail. */
 @Transactional public String change(String profileId,String user){
  if(!ScanProfile.exists(profileId))throw new IllegalArgumentException("Unbekanntes Scan-Profil.");
  var setting=repo.current().orElseGet(TenantScanProfile::new);
  String before=setting.getProfile()==null?ScanProfile.STANDARD.id():ScanProfile.byId(setting.getProfile()).id();
  setting.change(profileId,user);repo.save(setting);
  return before;
 }
}
