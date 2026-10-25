package de.ostms.lc.intake;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Settings of a mailbox channel (without the password). The connection is always encrypted (IMAPS or STARTTLS). Only mail from the listed senders is taken:
 * an address ("einkauf@kunde.example") or a whole domain ("@kunde.example"); an empty list takes nothing.
 */
public record MailboxSettings(String host,int port,String security,String username,String folder,String processedFolder,String rejectedFolder,List<String> allowedSenders){
 public static final String SSL="SSL",STARTTLS="STARTTLS";
 private static final Pattern HOST=Pattern.compile("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?");
 private static final Pattern FOLDER=Pattern.compile("[\\p{L}\\p{N} ._/-]{1,100}");
 private static final Pattern SENDER=Pattern.compile("(?:[A-Za-z0-9._%+'-]+)?@[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?");
 private static final ObjectMapper JSON=new ObjectMapper();
 public MailboxSettings{
  host=host==null?"":host.strip();
  if(!HOST.matcher(host).matches()||host.contains(".."))throw new IllegalArgumentException("Bitte einen gültigen Servernamen angeben.");
  if(port<1||port>65535)throw new IllegalArgumentException("Der Port muss zwischen 1 und 65535 liegen.");
  security=security==null?SSL:security.strip().toUpperCase(Locale.ROOT);
  if(!SSL.equals(security)&&!STARTTLS.equals(security))throw new IllegalArgumentException("Die Verbindung muss verschlüsselt sein (SSL oder STARTTLS).");
  username=username==null?"":username.strip();
  if(username.isEmpty()||username.length()>255)throw new IllegalArgumentException("Bitte einen Benutzernamen angeben.");
  folder=folder(folder,"INBOX","Der Ordner");processedFolder=folder(processedFolder,"LC-verarbeitet","Der Ordner für Verarbeitetes");rejectedFolder=folder(rejectedFolder,"LC-abgelehnt","Der Ordner für Abgelehntes");
  var clean=new ArrayList<String>();
  for(String s:allowedSenders==null?List.<String>of():allowedSenders){
   String v=s==null?"":s.strip().toLowerCase(Locale.ROOT);if(v.isEmpty())continue;
   if(!SENDER.matcher(v).matches()||v.length()>255)throw new IllegalArgumentException("Ungültiger Absender: "+(v.length()>60?v.substring(0,60)+"…":v));
   if(!clean.contains(v))clean.add(v);
  }
  if(clean.size()>100)throw new IllegalArgumentException("Höchstens 100 Absender.");
  allowedSenders=List.copyOf(clean);
 }
 private static String folder(String value,String fallback,String label){
  String v=value==null||value.isBlank()?fallback:value.strip();
  if(!FOLDER.matcher(v).matches()||v.contains(".."))throw new IllegalArgumentException(label+" hat einen ungültigen Namen.");
  return v;
 }
 /** True when mail from this address is taken: listed as address or its domain is listed. */
 public boolean allows(String address){
  if(address==null)return false;
  String a=address.strip().toLowerCase(Locale.ROOT);int at=a.lastIndexOf('@');
  if(at<1)return false;
  String domain=a.substring(at);
  return allowedSenders.contains(a)||allowedSenders.contains(domain);
 }
 public String toJson(){try{return JSON.writeValueAsString(this);}catch(tools.jackson.core.JacksonException impossible){throw new IllegalStateException(impossible);}}
 public static MailboxSettings fromJson(String json){try{return JSON.readValue(json,MailboxSettings.class);}catch(tools.jackson.core.JacksonException e){throw new IllegalStateException("Die Postfach-Einstellungen sind nicht lesbar.");}}
}
