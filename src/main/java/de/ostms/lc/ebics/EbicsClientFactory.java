package de.ostms.lc.ebics;

import org.kopi.ebics.client.EbicsClient;
import org.kopi.ebics.session.DefaultConfiguration;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

/**
 * Builds an {@link EbicsClient} configured for EBICS 3.0 (H005/A006/X002/E002). The library would otherwise read
 * H003/A005 from a bundled config.properties, hence the explicit subclass. The working directory only receives
 * the library's own trace files; key material lives encrypted in {@link EbicsConnection}, never there.
 */
@Component
public class EbicsClientFactory {
    private File root;
    private File properties;

    private synchronized void prepare() throws Exception {
        if (root != null) return;
        root = Files.createTempDirectory("ebics-client-").toFile();
        root.deleteOnExit();
        var props = new Properties();
        props.setProperty("countryCode", "DE");
        props.setProperty("languageCode", "de");
        props.setProperty("productName", "CorporateLCManager");
        properties = Files.createTempFile("ebics-client", ".properties").toFile();
        properties.deleteOnExit();
        try (var out = new FileOutputStream(properties)) { props.store(out, "lcm"); }
    }

    public EbicsClient newClient() throws Exception {
        prepare();
        var props = new Properties();
        props.setProperty("countryCode", "DE");
        props.setProperty("languageCode", "de");
        props.setProperty("productName", "CorporateLCManager");
        var configuration = new DefaultConfiguration(root, props) {
            @Override public Locale getLocale() { return Locale.GERMANY; }
            @Override public String getSignatureVersion() { return "A006"; }
            @Override public String getAuthenticationVersion() { return "X002"; }
            @Override public String getEncryptionVersion() { return "E002"; }
            @Override public String getVersion() { return "H005"; }
        };
        return new EbicsClient(configuration, new EbicsClient.ConfigProperties(properties));
    }
}
