package de.ostms.lc.intake;

import java.io.IOException;
import java.util.List;

/** An open connection to a mailbox. Reading never changes a mail; only {@link #finish} marks it as read and moves it, so a mail that could not be delivered is read again at the next run. */
public interface MailboxClient extends AutoCloseable {
 /** Unread mails, oldest first, at most max. */
 List<MailMessage> unseen(int max) throws IOException;
 /** Marks the mail as read and moves it to the folder (created when missing). */
 void finish(MailMessage message,String folder) throws IOException;
 @Override void close();
}
