package de.ostms.lc.ai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.util.*;

/**
 * Which AI endpoints a tenant may use. The operator lists the allowed hosts (lc.ai.allowed-hosts); everything else is refused, so a tenant administrator cannot point
 * the application at internal services (SSRF). External endpoints need HTTPS; private, loopback and link-local addresses are refused unless the operator allowed
 * local endpoints (lc.ai.allow-local, e.g. for Ollama).
 */
@Component
public class AiProviderPolicy {
 public record Preset(String id,String label,String baseUrl,boolean needsKey,boolean local){}
 public static final List<Preset> PRESETS=List.of(
  new Preset("mistral","Mistral","https://api.mistral.ai/v1",true,false),
  new Preset("openai","OpenAI","https://api.openai.com/v1",true,false),
  new Preset("custom","Eigener OpenAI-kompatibler Dienst","",true,false),
  new Preset("ollama","Ollama (lokal)","http://localhost:11434",false,true));
 private final Set<String> allowedHosts;private final boolean allowLocal;
 public AiProviderPolicy(@Value("${lc.ai.allowed-hosts:}") String hosts,@Value("${lc.ai.allow-local:false}") boolean allowLocal){
  var set=new TreeSet<String>();for(String h:hosts.split(","))if(!h.isBlank())set.add(h.trim().toLowerCase(Locale.ROOT));
  this.allowedHosts=Collections.unmodifiableSet(set);this.allowLocal=allowLocal;
 }
 public Set<String> allowedHosts(){return allowedHosts;}
 public boolean allowLocal(){return allowLocal;}
 public static Preset preset(String id){return PRESETS.stream().filter(p->p.id().equals(id)).findFirst().orElse(null);}

 /** The normalised base URL if the provider and address are allowed, else an IllegalArgumentException with a reason a tenant administrator can act on. */
 public String validate(String provider,String baseUrl){
  var preset=preset(provider);if(preset==null)throw new IllegalArgumentException("Unbekannter KI-Anbieter.");
  String raw=baseUrl==null||baseUrl.isBlank()?preset.baseUrl():baseUrl.trim();
  URI uri;try{uri=new URI(raw);}catch(URISyntaxException e){throw new IllegalArgumentException("Die Adresse ist ungültig.");}
  String host=uri.getHost()==null?"":uri.getHost().toLowerCase(Locale.ROOT);
  if(host.isEmpty()||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Die Adresse ist ungültig (kein Benutzername, keine Parameter).");
  if(preset.local()){
   if(!allowLocal)throw new IllegalArgumentException("Lokale KI-Dienste sind in dieser Installation nicht erlaubt.");
   if(!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme()))throw new IllegalArgumentException("Die Adresse muss mit http oder https beginnen.");
  }else{
   if(!"https".equals(uri.getScheme()))throw new IllegalArgumentException("Externe KI-Dienste müssen per HTTPS angebunden werden.");
   if(uri.getPort()!=-1&&uri.getPort()!=443)throw new IllegalArgumentException("Nur der Standard-Port 443 ist erlaubt.");
  }
  if(!allowedHosts.contains(host))throw new IllegalArgumentException("Der Host „"+host+"“ ist nicht freigegeben. Der Betreiber muss ihn in LC_AI_ALLOWED_HOSTS eintragen.");
  if(!preset.local())checkNotInternal(host);
  return raw.replaceAll("/+$","");
 }
 /** Resolves the host and refuses internal addresses; called when the endpoint is configured and again before every use (DNS can change). */
 public void checkNotInternal(String host){
  InetAddress[] all;try{all=InetAddress.getAllByName(host);}catch(UnknownHostException e){throw new IllegalArgumentException("Der Host „"+host+"“ ist nicht auflösbar.");}
  for(InetAddress a:all)if(internal(a))throw new IllegalArgumentException("Der Host „"+host+"“ zeigt auf eine interne Adresse und ist nicht erlaubt.");
 }
 static boolean internal(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return true;
  byte[] b=a.getAddress();
  if(b.length==16&&(b[0]&0xfe)==0xfc)return true;                       // IPv6 unique local fc00::/7
  if(b.length==4&&(b[0]&255)==100&&(b[1]&0xc0)==64)return true;         // 100.64.0.0/10 carrier-grade NAT
  if(b.length==4&&(b[0]&255)==169&&(b[1]&255)==254)return true;         // link-local / cloud metadata
  return false;
 }
}
