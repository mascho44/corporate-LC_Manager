package de.ostms.lc.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

/** Native Ollama API (local models). */
@Component
@ConditionalOnExpression("'${lc.ai.enabled:false}'=='true' && '${lc.ai.provider:ollama}'=='ollama'")
public class OllamaClient implements LlmClient {
 private static final ObjectMapper JSON=new ObjectMapper();
 private final URI endpoint;private final String model;private final Duration timeout;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public OllamaClient(@Value("${lc.ai.base-url:http://localhost:11434}") String baseUrl,@Value("${lc.ai.model:qwen2.5:3b}") String model,@Value("${lc.ai.timeout-seconds:240}") int timeoutSeconds){
  this.endpoint=URI.create(baseUrl.replaceAll("/+$","")+"/api/generate");this.model=model;this.timeout=Duration.ofSeconds(Math.max(10,timeoutSeconds));
 }
 @Override public String provider(){return "ollama";}
 @Override public String model(){return model;}
 @Override public String generateJson(String prompt)throws IOException{
  var body=JSON.writeValueAsString(Map.of("model",model,"prompt",prompt,"stream",false,"format","json","options",Map.of("temperature",0,"num_ctx",4096)));
  try{
   var response=http.send(HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new IOException("KI-Server antwortet mit HTTP "+response.statusCode());
   return JSON.readTree(response.body()).path("response").asText("");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("KI-Anfrage unterbrochen",e);}
 }
}
