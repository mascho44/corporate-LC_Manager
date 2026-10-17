package de.ostms.lc.intake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The server connects to the mailbox host, so it must not become a way to reach internal services: no local/private targets unless the host is allow-listed (INTAKE_ALLOWED_HOSTS). */
@Component
public class MailHostPolicy {
 private final Set<String> allowed;
 public MailHostPolicy(@Value("${app.intake.allowed-hosts:}") String allowedHosts){
  allowed=Arrays.stream(allowedHosts.split(",")).map(s->s.trim().toLowerCase(Locale.ROOT)).filter(s->!s.isEmpty()).collect(Collectors.toSet());
 }
 public void check(String host){
  String h=host==null?"":host.toLowerCase(Locale.ROOT);
  if(h.isEmpty())throw new IllegalArgumentException("Bitte einen Servernamen angeben.");
  if(allowed.contains(h))return;
  try{
   for(InetAddress address:InetAddress.getAllByName(h))
    if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()||uniqueLocalV6(address))
     throw new IllegalArgumentException("Der Server zeigt auf eine interne Adresse. Interne Hosts müssen in INTAKE_ALLOWED_HOSTS freigegeben werden.");
  }catch(java.net.UnknownHostException e){throw new IllegalArgumentException("Der Servername ist nicht auflösbar.");}
 }
 private static boolean uniqueLocalV6(InetAddress a){byte[] b=a.getAddress();return b.length==16&&(b[0]&0xfe)==0xfc;}
}
