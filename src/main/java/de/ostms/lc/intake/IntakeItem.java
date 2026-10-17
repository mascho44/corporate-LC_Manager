package de.ostms.lc.intake;

/** One file offered to the document inbox. */
public record IntakeItem(String filename,byte[] content,String contentType,IntakeOrigin origin){
 public IntakeItem{
  if(content==null||content.length==0)throw new IllegalArgumentException("Leere Dateien können nicht aufgenommen werden.");
  filename=filename==null||filename.isBlank()?"Dokument":filename.strip();
  if(origin==null)throw new IllegalArgumentException("Die Herkunft fehlt.");
 }
}
