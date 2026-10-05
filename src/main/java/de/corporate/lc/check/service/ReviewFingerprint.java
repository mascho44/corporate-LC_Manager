package de.corporate.lc.check.service;

import de.corporate.lc.check.api.CheckResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed values distinguish null, empty values and separator-containing evidence. */
public final class ReviewFingerprint {
    private ReviewFingerprint() {}
    public static String of(CheckResult result) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{result.ruleCatalogVersion(), result.rule().id(),
                    result.rule().version(), result.code(), result.message(), result.lcCondition(),
                    result.documentName(), result.documentEvidence(), result.inputFingerprint(),
                    result.automaticSeverity() == null ? null : result.automaticSeverity().name()}) {
                hash.update((value == null ? "-1:" : value.length() + ":" + value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
