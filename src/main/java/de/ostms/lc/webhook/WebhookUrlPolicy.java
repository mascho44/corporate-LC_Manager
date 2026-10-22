package de.ostms.lc.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** Targets of webhooks: https only and no internal addresses (loopback, private, link-local), checked when saving and again before every delivery. */
@Component
public class WebhookUrlPolicy {
 private final boolean allowPrivate;
 public WebhookUrlPolicy(@Value("${app.webhooks.allow-private-targets:false}") boolean allowPrivate){this.allowPrivate=allowPrivate;}

 /** Syntax and scheme only (no network access). */
 public URI parse(String url){
  URI uri;
  try{uri=new URI(url==null?"":url.trim());}catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Die Webhook-Adresse ist ungültig.");}
  String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(java.util.Locale.ROOT);
  if(uri.getHost()==null||!(scheme.equals("https")||(allowPrivate&&scheme.equals("http"))))throw new IllegalArgumentException(allowPrivate?"Die Webhook-Adresse muss mit https:// (oder http://) beginnen.":"Die Webhook-Adresse muss mit https:// beginnen.");
  if(uri.getUserInfo()!=null)throw new IllegalArgumentException("Die Webhook-Adresse darf keine Zugangsdaten enthalten.");
  if(url.trim().length()>500)throw new IllegalArgumentException("Die Webhook-Adresse ist zu lang.");
  return uri;
 }
 /** Resolves the host and rejects internal addresses. */
 public void checkTarget(URI uri){
  if(allowPrivate)return;
  try{for(InetAddress address:InetAddress.getAllByName(uri.getHost()))if(internal(address))throw new IllegalArgumentException("Die Webhook-Adresse zeigt auf ein internes Netz und ist nicht erlaubt.");}
  catch(UnknownHostException e){throw new IllegalArgumentException("Der Rechnername der Webhook-Adresse ist nicht auflösbar.");}
 }
 static boolean internal(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return true;
  byte[] b=a.getAddress();
  if(b.length==4)return (b[0]&0xff)==100&&((b[1]&0xff)&0xc0)==64; // 100.64.0.0/10 carrier-grade NAT
  return (b[0]&0xfe)==0xfc; // fc00::/7 unique local
 }
}
