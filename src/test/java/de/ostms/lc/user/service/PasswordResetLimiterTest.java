package de.ostms.lc.user.service;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
class PasswordResetLimiterTest {
    @Test void accountAndIpLimitsAreSeparate(){
        var limiter=new PasswordResetLimiter(Clock.fixed(Instant.now(),ZoneOffset.UTC));
        for(int i=0;i<3;i++)assertThat(limiter.allow("request","ip"+i,"User")).isTrue();
        assertThat(limiter.allow("request","other","user")).isFalse();
        for(int i=0;i<10;i++)assertThat(limiter.allow("complete","client","")).isTrue();
        assertThat(limiter.allow("complete","client","")).isFalse();
        assertThat(limiter.allow("complete","different","")).isTrue();
    }
}
