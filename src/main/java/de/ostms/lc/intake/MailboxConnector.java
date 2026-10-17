package de.ostms.lc.intake;

import java.io.IOException;

/** Opens a {@link MailboxClient}; the one implementation speaks IMAP (encrypted only), tests use a fake. */
public interface MailboxConnector {
 MailboxClient open(MailboxSettings settings,String password) throws IOException;
}
