package de.ostms.lc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/** Short URL for the separate platform console; authentication is enforced by the security chain. */
@Configuration
public class PlatformConsoleConfiguration implements WebMvcConfigurer {
 @Override public void addViewControllers(ViewControllerRegistry registry){registry.addViewController("/platform").setViewName("forward:/platform.html");}
}
