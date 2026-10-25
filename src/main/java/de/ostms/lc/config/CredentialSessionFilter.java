package de.ostms.lc.config;
import de.ostms.lc.user.repository.AppUserRepository;
import de.ostms.lc.user.service.CredentialStamp;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Revoke a session on its next request after a credential change or account removal. */
public class CredentialSessionFilter extends OncePerRequestFilter {
    public static final String STAMP="LC_CREDENTIAL_STAMP";
    public static final String AUTHORIZATION_STAMP="LC_AUTHORIZATION_STAMP";
    public static final String AUTHENTICATED_AT="LC_AUTHENTICATED_AT";
    public static final String TENANT="LC_SELECTED_TENANT";
    public static final String TOTP_VERIFIED="LC_TOTP_VERIFIED";
    private final AppUserRepository users;
    private final de.ostms.lc.tenant.service.TenantMembershipService memberships;
    public CredentialSessionFilter(AppUserRepository users,de.ostms.lc.tenant.service.TenantMembershipService memberships){this.users=users;this.memberships=memberships;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        // A scan-station token was already checked (and the tenant opened) by CaptureTokenFilter; there is no session to validate.
        if(auth!=null&&auth.getName()!=null&&auth.getName().startsWith("capture:")&&request.getHeader("Authorization")!=null){chain.doFilter(request,response);return;}
        // An API key was already checked (and the tenant opened) by ApiKeyFilter, which marks the request itself (a client cannot set request attributes); there is no session to validate either.
        if(auth!=null&&Boolean.TRUE.equals(request.getAttribute(de.ostms.lc.apikey.ApiKeyFilter.AUTHENTICATED))){chain.doFilter(request,response);return;}
        var session=request.getSession(false);
        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)){
            Object stamp=session==null?null:session.getAttribute(STAMP);
            Object started=session==null?null:session.getAttribute(AUTHENTICATED_AT);
            boolean withinLifetime=started instanceof Long&&System.currentTimeMillis()-(Long)started<28_800_000;
            var account=users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
            Object selected=session==null?null:session.getAttribute(TENANT);
            java.util.UUID tenantId=selected instanceof java.util.UUID id?id:de.ostms.lc.tenant.domain.Tenant.homeId();
            boolean valid=withinLifetime&&account!=null&&account.isActive()&&!account.isInvitationPending()&&CredentialStamp.of(account.getPasswordHash()).equals(stamp)&&
                de.ostms.lc.tenant.domain.Tenant.homeId().equals(account.getTenantId())&&
                (account.getAssignedRole()==null||account.getTenantId().equals(account.getAssignedRole().getTenantId()));
            valid=valid&&(selected==null||selected instanceof java.util.UUID);
            // Enrollment alone never upgrades a concurrent password-only session.
            valid=valid&&(account==null||!account.isTotpEnabled()||Boolean.TRUE.equals(session==null?null:session.getAttribute(TOTP_VERIFIED)));
            String uri=request.getRequestURI();
            boolean platformResource=uri.startsWith("/api/platform/")||uri.equals("/platform")||uri.startsWith("/platform.")||uri.startsWith("/platform-")||java.util.Set.of("/tenant-inventory.js","/i18n.js","/language-en.json","/language-de.json","/ui-feedback.js","/ui-feedback.css","/styles.css","/controls.css","/tenants.css").contains(uri);
            boolean globalAccess=valid&&platformResource&&account.isPlatformAdministrator()&&account.isTotpEnabled()&&Boolean.TRUE.equals(session.getAttribute(TOTP_VERIFIED));
            boolean platformIdentity=valid&&account.isPlatformAdministrator()&&account.isTotpEnabled()&&Boolean.TRUE.equals(session==null?null:session.getAttribute(TOTP_VERIFIED));
            boolean tenantless=false;
            if(valid&&!globalAccess){
                try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(tenantId)){
                    var access=memberships.requireActiveAccess(account.getId());
                    valid=(access.baseRole()!=de.ostms.lc.user.domain.UserRole.ADMIN||account.isTotpEnabled())&&
                        de.ostms.lc.user.service.AuthorizationStamp.of(access).equals(session.getAttribute(AUTHORIZATION_STAMP));
                }catch(org.springframework.security.access.AccessDeniedException denied){
                    // A platform administrator without a membership in the requested scope keeps the session: stray requests (favicon, an old link, the tenant start page)
                    // are refused or sent to the platform area instead of ending the platform session.
                    if(platformIdentity){tenantless=true;request.setAttribute(AccessDenialFilter.REASON,"Plattform-Sitzung ohne Mandantenzugriff");}else request.setAttribute(AccessDenialFilter.REASON,"keine aktive Mitgliedschaft im gewählten Mandanten");
                    valid=false;
                }
            }
            if(!valid&&tenantless){
                if(request.getRequestURI().startsWith("/api/")){response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+ServerMessages.translate("Kein Mandantenzugriff. Bitte die Plattformverwaltung nutzen.",request.getHeader("X-LC-Language"))+"\"}");}
                else if(request.getRequestURI().equals("/")||request.getRequestURI().endsWith(".html"))response.sendRedirect("/platform.html");
                else response.setStatus(404);
                return;
            }
            if(!valid){if(request.getAttribute(AccessDenialFilter.REASON)==null)request.setAttribute(AccessDenialFilter.REASON,"Sitzung abgelaufen, Zugangsdaten oder Rechte geändert");if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
                if(request.getRequestURI().startsWith("/api/")){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"error\":\""+ServerMessages.translate("Bitte erneut anmelden.",request.getHeader("X-LC-Language"))+"\"}");}
                else response.sendRedirect(uri.equals("/platform")||uri.startsWith("/platform.")||uri.startsWith("/platform-")?"/platform-login.html":"/login.html");
                return;
            }
            // Own identity self-service remains in the identity's home scope; business APIs use selected membership.
            boolean selfService=platformResource||(uri.startsWith("/api/profile")&&!uri.startsWith("/api/profile/language"))||uri.equals("/api/auth/password")||uri.startsWith("/api/auth/totp/");
            try(var scope=de.ostms.lc.tenant.domain.TenantContext.open(selfService?account.getTenantId():tenantId)){chain.doFilter(request,response);}
            return;
        }
        chain.doFilter(request,response);
    }
}
