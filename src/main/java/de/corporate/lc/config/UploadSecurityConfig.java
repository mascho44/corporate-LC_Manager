package de.corporate.lc.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class UploadSecurityConfig implements WebMvcConfigurer {
    private final UploadMalwareGuard guard;
    public UploadSecurityConfig(UploadMalwareGuard guard){this.guard=guard;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(guard).addPathPatterns("/api/**");}
}
