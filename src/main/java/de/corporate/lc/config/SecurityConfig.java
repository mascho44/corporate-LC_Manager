package de.corporate.lc.config;
import de.corporate.lc.user.service.AppUserDetailsService; import jakarta.servlet.http.HttpServletResponse; import org.springframework.context.annotation.*; import org.springframework.http.HttpStatus; import org.springframework.security.authentication.*; import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.*; import org.springframework.security.web.authentication.HttpStatusEntryPoint; import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
@Configuration public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean SecurityFilterChain security(HttpSecurity http,AppUserDetailsService users)throws Exception{return http.userDetailsService(users)
  .authorizeHttpRequests(a->a
   .requestMatchers("/login.html","/login.js","/styles.css","/api/auth/login","/api/auth/login/totp","/api/health","/actuator/health","/actuator/health/liveness","/actuator/health/readiness").permitAll()
   .requestMatchers("/actuator/**").hasRole("ADMIN")
   .requestMatchers("/api/audit/**").hasAuthority("PERM_AUDIT_VIEW")
   .requestMatchers("/api/document-templates/**","/api/company-profile/**").hasAuthority("PERM_SETTINGS_MANAGE")
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/training/**").authenticated()
   .requestMatchers("/api/training/**").hasAuthority("PERM_TRAINING_MANAGE")
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/users/assignable").authenticated()
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/lcs/*/document-drafts/**").authenticated()
   .requestMatchers("/api/users/**","/api/roles/**").hasAuthority("PERM_USER_MANAGE")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*").hasAuthority("PERM_LC_DELETE")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/documents/*").hasAuthority("PERM_DOCUMENT_DELETE")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/imports","/api/imports/preview","/api/imports/file-preview","/api/lcs/import/**").hasAuthority("PERM_SWIFT_IMPORT")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/documents","/api/lcs/*/documents/archive","/api/lcs/*/documents/batch").hasAuthority("PERM_DOCUMENT_UPLOAD")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/generated-documents","/api/lcs/*/generated-documents/docx").hasAuthority("PERM_DOCUMENT_GENERATE")
   .requestMatchers("/api/lcs/*/emails","/api/lcs/*/emails/**").hasAuthority("PERM_EMAIL_SEND")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/document-checks/decisions").hasAuthority("PERM_DOCUMENT_REVIEW")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/notes","/api/lcs/*/follow-up/complete","/api/lcs/*/assign-to-me","/api/lcs/*/tasks").hasAuthority("PERM_LC_EDIT")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/document-drafts/**").hasAuthority("PERM_DOCUMENT_GENERATE")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/document-drafts/**").hasAuthority("PERM_DOCUMENT_REVIEW")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/document-drafts/**").hasAuthority("PERM_DOCUMENT_DELETE")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/tasks/*/complete").hasAuthority("PERM_LC_EDIT")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/document-checks/requirements").hasAuthority("PERM_DOCUMENT_REVIEW")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/tasks/*").hasAuthority("PERM_LC_EDIT")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/document-checks/requirements").hasAuthority("PERM_DOCUMENT_REVIEW")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/documents/*").hasAuthority("PERM_DOCUMENT_UPLOAD")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*").hasAuthority("PERM_LC_EDIT")
   .anyRequest().authenticated())
  .csrf(c->c.ignoringRequestMatchers("/api/auth/login","/api/auth/login/totp"))
  .exceptionHandling(e->e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),new AntPathRequestMatcher("/api/**")))
  .formLogin(f->f.loginPage("/login.html").permitAll()).logout(l->l.disable())
  .headers(h->h.frameOptions(f->f.deny()).contentTypeOptions(c->{}))
  .build();}
}
