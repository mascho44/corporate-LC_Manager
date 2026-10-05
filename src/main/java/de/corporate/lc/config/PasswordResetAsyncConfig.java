package de.corporate.lc.config;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration @EnableAsync
public class PasswordResetAsyncConfig {
    @Bean("passwordResetExecutor") public Executor passwordResetExecutor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);executor.setThreadNamePrefix("password-reset-");executor.initialize();return executor;
    }
}
