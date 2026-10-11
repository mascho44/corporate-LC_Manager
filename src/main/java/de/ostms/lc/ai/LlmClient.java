package de.ostms.lc.ai;
/** A language model that answers a prompt with one JSON document. */
public interface LlmClient {
 String model();
 /** @throws java.io.IOException if the model cannot be reached or does not answer in time */
 String generateJson(String prompt) throws java.io.IOException;
}
