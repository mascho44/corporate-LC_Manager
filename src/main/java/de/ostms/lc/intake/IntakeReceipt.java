package de.ostms.lc.intake;

import java.util.UUID;

/** What became of one offered file: CREATED (new inbox entry) or DUPLICATE (the same file arrived before; nothing was stored again). */
public record IntakeReceipt(String filename,String status,UUID inboxItemId,String sha256,Integer declaredPages,Integer actualPages,boolean pageCountMatches,String note){
 public static final String CREATED="CREATED",DUPLICATE="DUPLICATE";
 public boolean created(){return CREATED.equals(status);}
}
