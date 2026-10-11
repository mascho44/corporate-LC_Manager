package de.ostms.lc.ai;
/** A language model that answers a prompt with one JSON document. Implementations are interchangeable; nothing else in the application knows a provider. */
public interface LlmClient {
 /** Short provider label for the audit trail, e.g. "ollama" or "openai-compatible". */
 String provider();
 String model();
 /** @throws java.io.IOException if the model cannot be reached or does not answer in time */
 String generateJson(String prompt) throws java.io.IOException;
}
