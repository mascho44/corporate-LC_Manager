package de.ostms.lc.intake;

import java.io.IOException;
import java.util.List;

/**
 * A source the application fetches files from on its own (a watched folder, a mailbox). A channel finds the files, hands them to the {@link IntakeSink} and
 * acts on the receipts afterwards (move the file, mark the mail as read) - so a file that could not be delivered is never lost. Channels that receive
 * pushes (the scan station REST interface) call the {@link InboxIntakeService} directly instead.
 */
public interface IntakeChannel {
 /** Where delivered files go: the common intake; one call per batch, one receipt per file in the same order. */
 @FunctionalInterface interface IntakeSink { List<IntakeReceipt> deliver(List<IntakeItem> items) throws IOException; }
 /** Outcome of one run. */
 record Result(int created,int duplicates,int failed,int waiting,String note){
  public String summary(){
   String text=created+" neu, "+duplicates+" bereits vorhanden, "+failed+" fehlgeschlagen"+(waiting>0?", "+waiting+" noch in Bearbeitung des Scanners":"");
   return note==null||note.isBlank()?text:text+" · "+note;
  }
 }
 /** Channel type as stored with the configuration, e.g. FOLDER. */
 String type();
 /** One run for the given configuration inside the tenant scope. */
 Result poll(IntakeChannelConfig config,IntakeSink sink) throws IOException;
}
