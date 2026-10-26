package de.ostms.lc.calendarfeed;

import de.ostms.lc.lc.service.LcCalendarService;
import de.ostms.lc.tenant.domain.TenantContext;
import de.ostms.lc.tenant.repository.TenantRepository;
import de.ostms.lc.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The calendar subscription of a person: a secret link (/calendar/feed/…) that a calendar app polls without a login. It shows the dates of the files assigned to that
 * person, nothing else. The person can replace the link (the old one stops working at once) or switch it off. Only the hash of the link is stored.
 */
@Service
public class CalendarFeedService {
 public static final String PREFIX="lcf_";
 public record Status(boolean active,String prefix,LocalDateTime createdAt,LocalDateTime lastUsedAt){}
 public record Created(Status status,String secret){}
 private final CalendarFeedRepository feeds;private final TenantRepository tenants;private final AppUserRepository users;private final LcCalendarService calendar;
 private final SecureRandom random=new SecureRandom();
 public CalendarFeedService(CalendarFeedRepository feeds,TenantRepository tenants,AppUserRepository users,LcCalendarService calendar){this.feeds=feeds;this.tenants=tenants;this.users=users;this.calendar=calendar;}

 static String hash(String secret){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 static Status status(CalendarFeed feed){return feed==null?new Status(false,null,null,null):new Status(true,feed.getTokenPrefix(),feed.getCreatedAt(),feed.getLastUsedAt());}

 @Transactional(readOnly=true) public Status status(String username){return status(feeds.forUser(TenantContext.currentId(),username).orElse(null));}

 /** The secret is returned once. A new one replaces an existing link of the same person. */
 @Transactional public Created create(String username){
  UUID tenant=TenantContext.currentId();
  feeds.forUser(tenant,username).ifPresent(old->{feeds.delete(old);feeds.flush();});
  byte[] bytes=new byte[32];random.nextBytes(bytes);
  String secret=PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  var feed=feeds.save(new CalendarFeed(tenant,username.toLowerCase(Locale.ROOT),hash(secret),secret.substring(0,PREFIX.length()+4)));
  return new Created(status(feed),secret);
 }
 @Transactional public void revoke(String username){feeds.forUser(TenantContext.currentId(),username).ifPresent(feeds::delete);}

 /** The calendar file for a link; empty when the link is unknown, or the person or the tenant is no longer active. The lookup runs before the tenant is known, in the deliberate platform scope. */
 public Optional<byte[]> export(String secret){
  if(secret==null||!secret.startsWith(PREFIX)||secret.length()>200)return Optional.empty();
  CalendarFeed feed;
  try(var platform=TenantContext.openPlatform()){feed=touch(secret);}
  if(feed==null)return Optional.empty();
  try(var scope=TenantContext.open(feed.getTenantId())){return build(feed);}
 }
 @Transactional CalendarFeed touch(String secret){
  var feed=feeds.findByTokenHash(hash(secret)).orElse(null);
  if(feed==null)return null;
  var now=LocalDateTime.now();
  if(feed.getLastUsedAt()==null||feed.getLastUsedAt().isBefore(now.minusMinutes(1))){feed.used(now);feeds.save(feed);}
  return feed;
 }
 @Transactional(readOnly=true) Optional<byte[]> build(CalendarFeed feed){
  var tenant=tenants.findById(feed.getTenantId()).orElse(null);
  if(tenant==null||!tenant.isActive()||!users.existsAssignableUsername(feed.getUsername()))return Optional.empty();
  return Optional.of(calendar.export("mine",feed.getUsername()));
 }
}
