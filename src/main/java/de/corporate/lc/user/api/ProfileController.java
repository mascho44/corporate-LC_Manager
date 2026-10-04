package de.corporate.lc.user.api;

import de.corporate.lc.audit.service.AuditService;
import de.corporate.lc.user.service.ProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;
    private final AuditService audit;
    public ProfileController(ProfileService service,AuditService audit){this.service=service;this.audit=audit;}
    public record UpdateRequest(@NotBlank @Size(max=255) String displayName) { }

    @GetMapping public ProfileView profile(Authentication auth){return service.profile(auth.getName());}
    @PutMapping public ProfileView update(@Valid @RequestBody UpdateRequest request,Authentication auth){
        String previous=service.profile(auth.getName()).displayName();
        ProfileView result=service.updateName(auth.getName(),request.displayName());
        audit.recordChange(auth,"USER_PROFILE_UPDATED","USER",auth.getName(),"Eigenen Anzeigenamen geändert",previous,result.displayName());return result;
    }
    @PostMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileView upload(@RequestPart("file") MultipartFile file,Authentication auth){
        ProfileView result=service.uploadAvatar(auth.getName(),file);audit.record(auth,"USER_AVATAR_UPDATED","USER",auth.getName(),"Eigenes Profilbild geändert");return result;
    }
    @GetMapping("/avatar") public ResponseEntity<byte[]> avatar(Authentication auth){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(service.avatar(auth.getName()));}
    @DeleteMapping("/avatar") public ProfileView delete(Authentication auth){ProfileView result=service.deleteAvatar(auth.getName());audit.record(auth,"USER_AVATAR_DELETED","USER",auth.getName(),"Eigenes Profilbild entfernt");return result;}
}
