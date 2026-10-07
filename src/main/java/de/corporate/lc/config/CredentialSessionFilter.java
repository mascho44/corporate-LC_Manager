package de.corporate.lc.config;
import de.corporate.lc.user.repository.AppUserRepository;
import de.corporate.lc.user.service.CredentialStamp;
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
    private final de.corporate.lc.tenant.service.TenantMembershipService memberships;
    public CredentialSessionFilter(AppUserRepository users,de.corporate.lc.tenant.service.TenantMembershipService memberships){this.users=users;this.memberships=memberships;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var session=request.getSession(false);
        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)){
            Object stamp=session==null?null:session.getAttribute(STAMP);
            Object started=session==null?null:session.getAttribute(AUTHENTICATED_AT);
            boolean withinLifetime=started instanceof Long&&System.currentTimeMillis()-(Long)started<28_800_000;
            var account=users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
            Object selected=session==null?null:session.getAttribute(TENANT);
            java.util.UUID tenantId=selected instanceof java.util.UUID id?id:de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID;
            boolean valid=withinLifetime&&account!=null&&account.isActive()&&!account.isInvitationPending()&&CredentialStamp.of(account.getPasswordHash()).equals(stamp)&&
                de.corporate.lc.tenant.domain.Tenant.DEFAULT_ID.equals(account.getTenantId())&&
                (account.getAssignedRole()==null||account.getTenantId().equals(account.getAssignedRole().getTenantId()));
            valid=valid&&(selected==null||selected instanceof java.util.UUID);
            // Enrollment alone never upgrades a concurrent password-only session.
            valid=valid&&(account==null||!account.isTotpEnabled()||Boolean.TRUE.equals(session==null?null:session.getAttribute(TOTP_VERIFIED)));
            if(valid){
                try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(tenantId)){
                    var access=memberships.requireActiveAccess(account.getId());
                    valid=(access.baseRole()!=de.corporate.lc.user.domain.UserRole.ADMIN||account.isTotpEnabled())&&
                        de.corporate.lc.user.service.AuthorizationStamp.of(access).equals(session.getAttribute(AUTHORIZATION_STAMP));
                }catch(org.springframework.security.access.AccessDeniedException denied){valid=false;}
            }
            if(!valid){if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
                if(request.getRequestURI().startsWith("/api/")){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"error\":\"Bitte erneut anmelden.\"}");}
                else response.sendRedirect("/login.html");
                return;
            }
            // Own identity self-service remains in the identity's home scope; business APIs use selected membership.
            String uri=request.getRequestURI();boolean selfService=(uri.startsWith("/api/profile")&&!uri.startsWith("/api/profile/language"))||uri.equals("/api/auth/password")||uri.startsWith("/api/auth/totp/");
            try(var scope=de.corporate.lc.tenant.domain.TenantContext.open(selfService?account.getTenantId():tenantId)){chain.doFilter(request,response);}
            return;
        }
        chain.doFilter(request,response);
    }
}
