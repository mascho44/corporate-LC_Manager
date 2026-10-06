package de.corporate.lc.user.service;
import de.corporate.lc.user.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.stream.Stream;

/** Deterministic snapshot of the authorities granted at authentication. */
public final class AuthorizationStamp {
 private AuthorizationStamp(){}
 public static String of(Collection<? extends GrantedAuthority> authorities){return hash(authorities.stream().map(GrantedAuthority::getAuthority));}
 public static String of(AppUser user){return hash(Stream.concat(Stream.of("ROLE_"+user.getRole().name()),user.effectivePermissions().stream().map(p->"PERM_"+p.name())));}
 private static String hash(Stream<String> authorities){return CredentialStamp.of(authorities.distinct().sorted().collect(java.util.stream.Collectors.joining("\n")));}
}
