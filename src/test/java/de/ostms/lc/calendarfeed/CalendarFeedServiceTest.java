package de.ostms.lc.calendarfeed;

import de.ostms.lc.lc.service.LcCalendarService;
import de.ostms.lc.tenant.domain.Tenant;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CalendarFeedServiceTest {
 private final CalendarFeedRepository feeds=mock(CalendarFeedRepository.class);private final TenantRepository tenants=mock(TenantRepository.class);
 private final AppUserRepository users=mock(AppUserRepository.class);private final LcCalendarService calendar=mock(LcCalendarService.class);
 private final CalendarFeedService service=new CalendarFeedService(feeds,tenants,users,calendar);
 private final UUID tenantId=UUID.randomUUID();

 @Test void createsASecretLinkAndStoresOnlyItsHash() {
  when(feeds.save(any())).thenAnswer(i->i.getArgument(0));when(feeds.forUser(any(),anyString())).thenReturn(Optional.empty());
  try(var scope=TenantContext.open(tenantId)){
   var created=service.create("Anna");
   assertThat(created.secret()).startsWith("lcf_").hasSizeGreaterThan(40);
   assertThat(created.status().active()).isTrue();
   assertThat(created.status().prefix()).isEqualTo(created.secret().substring(0,8));
   var saved=org.mockito.ArgumentCaptor.forClass(CalendarFeed.class);verify(feeds).save(saved.capture());
   assertThat(saved.getValue().getUsername()).isEqualTo("anna");
   assertThat(created.status().toString()).doesNotContain(created.secret());
  }
 }
 @Test void aNewLinkReplacesTheOldOne() {
  var old=new CalendarFeed(tenantId,"anna","x","lcf_abcd");
  when(feeds.save(any())).thenAnswer(i->i.getArgument(0));when(feeds.forUser(any(),anyString())).thenReturn(Optional.of(old));
  try(var scope=TenantContext.open(tenantId)){service.create("Anna");}
  verify(feeds).delete(old);
 }
 @Test void unknownOrMalformedLinksGiveNothing() {
  when(feeds.findByTokenHash(anyString())).thenReturn(Optional.empty());
  assertThat(service.export("lcf_unknown")).isEmpty();
  assertThat(service.export("not-a-link")).isEmpty();
  assertThat(service.export(null)).isEmpty();
  verifyNoInteractions(calendar);
 }
 @Test void aValidLinkDeliversOnlyTheOwnFilesOfThatPerson() {
  var feed=new CalendarFeed(tenantId,"anna",CalendarFeedService.hash("lcf_geheim"),"lcf_");
  when(feeds.findByTokenHash(CalendarFeedService.hash("lcf_geheim"))).thenReturn(Optional.of(feed));
  var tenant=mock(Tenant.class);when(tenant.isActive()).thenReturn(true);when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
  when(users.existsAssignableUsername("anna")).thenReturn(true);
  when(calendar.export("mine","anna")).thenReturn("BEGIN:VCALENDAR".getBytes());
  assertThat(service.export("lcf_geheim")).hasValueSatisfying(b->assertThat(new String(b)).startsWith("BEGIN:VCALENDAR"));
 }
 @Test void aLinkOfSomeoneWhoLeftTheTenantOrOfAnInactiveTenantStopsWorking() {
  var feed=new CalendarFeed(tenantId,"anna",CalendarFeedService.hash("lcf_geheim"),"lcf_");
  when(feeds.findByTokenHash(any())).thenReturn(Optional.of(feed));
  var tenant=mock(Tenant.class);when(tenant.isActive()).thenReturn(true);when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
  when(users.existsAssignableUsername("anna")).thenReturn(false);
  assertThat(service.export("lcf_geheim")).isEmpty();
  when(users.existsAssignableUsername("anna")).thenReturn(true);when(tenant.isActive()).thenReturn(false);
  assertThat(service.export("lcf_geheim")).isEmpty();
  verify(calendar,never()).export(anyString(),anyString());
 }
}
