package de.corporate.lc.document.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** No unread pipes; always terminate a child on timeout or interruption. */
public final class BoundedProcess {
    private BoundedProcess() {}
    public static final class UnavailableException extends IOException {
        UnavailableException(String tool, IOException cause) { super(tool + ": Programm konnte nicht gestartet werden.", cause); }
    }
    public static final class TimeoutException extends IOException {
        TimeoutException(String tool) { super(tool + ": Dokumentenverarbeitung hat das Zeitlimit überschritten."); }
    }
    public static void run(ProcessBuilder builder, long seconds) throws IOException, InterruptedException {
        if(builder.redirectOutput()==ProcessBuilder.Redirect.PIPE)builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        String tool=java.nio.file.Path.of(builder.command().get(0)).getFileName().toString();
        Process process;
        try { process=builder.start(); } catch(IOException cause) { throw new UnavailableException(tool,cause); }
        try {
            process.getOutputStream().close();
            if(!process.waitFor(seconds,TimeUnit.SECONDS))throw new TimeoutException(tool);
            if(process.exitValue()!=0)throw new IOException(tool + ": Dokumentenverarbeitung fehlgeschlagen (Exit " + process.exitValue() + ").");
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
