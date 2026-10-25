package de.ostms.lc.intake;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Where a file came from. The channel is one of {@link #CHANNELS}; reference is the id the channel can find the item again with (Message-ID, file path),
 * device and batch describe a scan station, sender and subject an e-mail. sha256 is the checksum the sender announced (verified when given), actor the
 * name stored as "received by".
 */
public record IntakeOrigin(String channel,String reference,String batchId,String device,String sender,String subject,Integer declaredPages,LocalDateTime sentAt,String sha256,String actor){
 public static final String SCAN_STATION="SCAN_STATION",EMAIL="EMAIL",FOLDER="FOLDER";
 public static final Set<String> CHANNELS=Set.of(SCAN_STATION,EMAIL,FOLDER);
 public IntakeOrigin{
  if(channel==null||!CHANNELS.contains(channel))throw new IllegalArgumentException("Unbekannter Eingangskanal.");
  if(actor==null||actor.isBlank())throw new IllegalArgumentException("Der Empfänger fehlt.");
  reference=limit(reference,255,"Die Referenz");batchId=limit(batchId,100,"Die Stapel-Kennung");device=limit(device,100,"Die Geräte-Kennung");
  sender=limit(sender,255,"Der Absender");subject=limit(subject,255,"Der Betreff");
  if(declaredPages!=null&&(declaredPages<1||declaredPages>10000))throw new IllegalArgumentException("Die Seitenzahl muss zwischen 1 und 10000 liegen.");
 }
 private static String limit(String value,int max,String label){
  if(value==null||value.isBlank())return null;
  String v=value.strip();
  if(v.length()>max)throw new IllegalArgumentException(label+" ist zu lang (höchstens "+max+" Zeichen).");
  return v;
 }
}
