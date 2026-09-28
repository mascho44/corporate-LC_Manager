package de.corporate.lc.config;
import de.corporate.lc.user.service.AppUserDetailsService; import jakarta.servlet.http.HttpServletResponse; import org.springframework.context.annotation.*; import org.springframework.http.HttpStatus; import org.springframework.security.authentication.*; import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.*; import org.springframework.security.web.authentication.HttpStatusEntryPoint; import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
@Configuration public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean SecurityFilterChain security(HttpSecurity http,AppUserDetailsService users)throws Exception{return http.userDetailsService(users)
  .authorizeHttpRequests(a->a
   .requestMatchers("/login.html","/login.js","/styles.css","/api/auth/login","/api/health","/actuator/health","/actuator/health/liveness","/actuator/health/readiness").permitAll()
   .requestMatchers("/actuator/**").hasRole("ADMIN")
   .requestMatchers("/api/audit/**").hasRole("ADMIN")
   .requestMatchers("/api/document-templates/**").hasRole("ADMIN")
   .requestMatchers("/api/training/**").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/users/assignable").authenticated()
   .requestMatchers("/api/users/**").hasRole("ADMIN")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*").hasRole("ADMIN")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/documents/*").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/imports","/api/imports/preview","/api/imports/file-preview","/api/lcs/import/**","/api/lcs/*/documents","/api/lcs/*/documents/archive","/api/lcs/*/generated-documents","/api/lcs/*/generated-documents/docx").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/document-checks/decisions").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/notes","/api/lcs/*/follow-up/complete","/api/lcs/*/assign-to-me").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/tasks").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/tasks/*/complete").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*/document-checks/requirements").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/tasks/*").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/document-checks/requirements").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*","/api/lcs/*/documents/*").hasAnyRole("ADMIN","EDITOR")
   .anyRequest().authenticated())
  .csrf(c->c.ignoringRequestMatchers("/api/auth/login"))
  .exceptionHandling(e->e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),new AntPathRequestMatcher("/api/**")))
  .formLogin(f->f.loginPage("/login.html").permitAll()).logout(l->l.disable())
  .headers(h->h.frameOptions(f->f.deny()).contentTypeOptions(c->{}))
  .build();}
}
