package de.ostms.lc.tenant.service;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads lc.tenant.separate-platform-admin (default true). */
@Component
public class PlatformSeparationConfig {
 private final boolean separate;
 public PlatformSeparationConfig(@Value("${lc.tenant.separate-platform-admin:true}") boolean separate){this.separate=separate;}
 @PostConstruct void apply(){PlatformSeparationPolicy.enabled(separate);}
}
