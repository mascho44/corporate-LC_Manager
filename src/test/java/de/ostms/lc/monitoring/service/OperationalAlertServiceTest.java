package de.ostms.lc.monitoring.service;

import de.ostms.lc.capture.CaptureTokenService;
import de.ostms.lc.sanctions.SanctionsService;
import de.ostms.lc.user.domain.AppUser;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationalAlertServiceTest {
    @Test void staleFailedAndMissingListsAreReported() {
        var now=LocalDateTime.of(2026,10,15,8,0);
        var status=new SanctionsService.Status(true,false,List.of(
            new SanctionsService.SourceStatus("EU",now.minusHours(3),6000,"a",null,null),
            new SanctionsService.SourceStatus("UN",now.minusDays(3),1000,"b",null,"IOException: timeout"),
            new SanctionsService.SourceStatus("OFAC",null,0,null,null,null)));
        var problems=OperationalAlertService.sanctionsProblems(status,now);
        assertThat(problems).hasSize(2);assertThat(problems.get(0)).startsWith("UN: Stand vom 2026-10-12").contains("fehlgeschlagen");assertThat(problems.get(1)).startsWith("OFAC: noch nicht geladen");
        assertThat(OperationalAlertService.sanctionsProblems(new SanctionsService.Status(false,false,status.sources()),now)).isEmpty();
    }

    @Test void silentStationsGoToTheAdministratorsAtMostOnceADay() {
        var captures=mock(CaptureTokenService.class);var users=mock(AppUserRepository.class);var mail=mock(JavaMailSender.class);
        var today=LocalDate.of(2026,10,15);
        when(captures.silent(today)).thenReturn(List.of(new CaptureTokenService.Silent("Poststelle EG",3,false),new CaptureTokenService.Silent("Scanner 2",0,true)));
        var admin=mock(AppUser.class);when(admin.getEmail()).thenReturn("admin@example.test");
        when(users.findActiveTenantAdministrators()).thenReturn(List.of(admin));
        var service=new OperationalAlertService(captures,mock(SanctionsService.class),users,mail,true,"lc@example.test","");
        assertThat(service.silentStations(today)).isEqualTo(1);
        assertThat(service.silentStations(today)).isZero();
        var body=OperationalAlertService.silentBody(captures.silent(today),"");
        assertThat(body).contains("Poststelle EG: seit 3 Arbeitstagen keine Lieferung","Scanner 2: noch nie genutzt");
        verify(mail,times(1)).send(any(SimpleMailMessage.class));
        assertThat(new OperationalAlertService(captures,mock(SanctionsService.class),users,mail,false,"lc@example.test","").silentStations(today)).isZero();
    }

    @Test
    void anUnchangedListThatWasCheckedRecentlyIsNotStale() {
        var now = java.time.LocalDateTime.of(2026, 10, 18, 17, 0);
        var status = new SanctionsService.Status(true, false, java.util.List.of(
            new SanctionsService.SourceStatus("EU", now.minusDays(3), 6000, "a", null, null, now.minusHours(14))));
        assertThat(OperationalAlertService.sanctionsProblems(status, now)).isEmpty();
        var neverChecked = new SanctionsService.Status(true, false, java.util.List.of(
            new SanctionsService.SourceStatus("EU", now.minusDays(3), 6000, "a", null, null)));
        assertThat(OperationalAlertService.sanctionsProblems(neverChecked, now)).hasSize(1);
    }
}
