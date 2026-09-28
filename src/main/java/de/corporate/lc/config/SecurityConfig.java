package de.corporate.lc.config;
import de.corporate.lc.user.service.AppUserDetailsService; import jakarta.servlet.http.HttpServletResponse; import org.springframework.context.annotation.*; import org.springframework.http.HttpStatus; import org.springframework.security.authentication.*; import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.security.web.*; import org.springframework.security.web.authentication.HttpStatusEntryPoint; import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
@Configuration public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean SecurityFilterChain security(HttpSecurity http,AppUserDetailsService users)throws Exception{return http.userDetailsService(users)
  .authorizeHttpRequests(a->a
   .requestMatchers("/login.html","/login.js","/styles.css","/api/auth/login","/api/health").permitAll()
   .requestMatchers("/api/audit/**").hasRole("ADMIN")
   .requestMatchers("/api/training/**").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers("/api/users/**").hasRole("ADMIN")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*").hasRole("ADMIN")
   .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/lcs/*/documents/*").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/imports","/api/imports/preview","/api/imports/file-preview","/api/lcs/import/**","/api/lcs/*/documents","/api/lcs/*/documents/archive","/api/lcs/*/generated-documents").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/lcs/*/document-checks/decisions").hasAnyRole("ADMIN","EDITOR")
   .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/lcs/*").hasAnyRole("ADMIN","EDITOR")
   .anyRequest().authenticated())
  .csrf(c->c.ignoringRequestMatchers("/api/auth/login"))
  .exceptionHandling(e->e.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),new AntPathRequestMatcher("/api/**")))
  .formLogin(f->f.loginPage("/login.html").permitAll()).logout(l->l.disable())
  .headers(h->h.frameOptions(f->f.deny()).contentTypeOptions(c->{}))
  .build();}
}
