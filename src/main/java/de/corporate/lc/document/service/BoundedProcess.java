package de.corporate.lc.document.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** No unread pipes; always terminate a child on timeout or interruption. */
public final class BoundedProcess {
    private BoundedProcess() {}
    public static void run(ProcessBuilder builder, long seconds) throws IOException, InterruptedException {
        if(builder.redirectOutput()==ProcessBuilder.Redirect.PIPE)builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process=builder.start();
        try {
            process.getOutputStream().close();
            if(!process.waitFor(seconds,TimeUnit.SECONDS))throw new IOException("Dokumentenverarbeitung hat das Zeitlimit überschritten.");
            if(process.exitValue()!=0)throw new IOException("Dokumentenverarbeitung fehlgeschlagen.");
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            if(process.isAlive()){
                process.descendants().forEach(child->child.destroyForcibly());
                process.destroyForcibly();
                try { process.waitFor(5,TimeUnit.SECONDS); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
    }
}
