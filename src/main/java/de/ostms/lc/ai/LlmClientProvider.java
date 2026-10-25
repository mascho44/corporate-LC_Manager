package de.ostms.lc.ai;
import java.util.Optional;
/** The model to use for the current tenant, if any. */
public interface LlmClientProvider {
 Optional<LlmClient> current();
}
