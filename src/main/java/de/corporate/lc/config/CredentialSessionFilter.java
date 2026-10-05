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
    public static final String AUTHENTICATED_AT="LC_AUTHENTICATED_AT";
    private final AppUserRepository users;
    public CredentialSessionFilter(AppUserRepository users){this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var session=request.getSession(false);
        if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)&&session!=null){
            Object stamp=session.getAttribute(STAMP);
            Object started=session.getAttribute(AUTHENTICATED_AT);
            boolean withinLifetime=started instanceof Long&&System.currentTimeMillis()-(Long)started<28_800_000;
            boolean valid=withinLifetime&&users.findByUsernameIgnoreCase(auth.getName()).filter(u->u.isActive()&&CredentialStamp.of(u.getPasswordHash()).equals(stamp)&&
                (u.getRole()!=de.corporate.lc.user.domain.UserRole.ADMIN||u.isTotpEnabled())).isPresent();
            if(!valid){session.invalidate();SecurityContextHolder.clearContext();
                if(request.getRequestURI().startsWith("/api/")){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"error\":\"Bitte erneut anmelden.\"}");}
                else response.sendRedirect("/login.html");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
