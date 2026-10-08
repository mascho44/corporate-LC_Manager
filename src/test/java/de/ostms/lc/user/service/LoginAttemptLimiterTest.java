package de.ostms.lc.user.service;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LoginAttemptLimiterTest {
    @Test void limitsAccountAcrossAddressesAndCase() {
        var limiter=new LoginAttemptLimiter();
        for(int i=0;i<5;i++)assertThat(limiter.allow("password"," Admin ","ip"+i)).isTrue();
        assertThat(limiter.allow("password","admin","new-ip")).isFalse();
        assertThat(limiter.allow("totp","admin","new-ip")).isTrue();
    }
    @Test void successfulAccountsDoNotResetIpBudget() {
        var limiter=new LoginAttemptLimiter();
        for(int i=0;i<30;i++){assertThat(limiter.allow("password","user"+i,"ip")).isTrue();limiter.succeeded("password","user"+i);}
        assertThat(limiter.allow("password","next","ip")).isFalse();
    }
    @Test void windowExpires() {
        var clock=new MutableClock();var limiter=new LoginAttemptLimiter(clock);
        for(int i=0;i<5;i++)assertThat(limiter.allow("totp","user","ip")).isTrue();
        assertThat(limiter.allow("totp","user","ip")).isFalse();
        clock.now=300_000;
        assertThat(limiter.allow("totp","user","ip")).isTrue();
    }
    private static class MutableClock extends Clock {
        long now;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now);}
    }
}
