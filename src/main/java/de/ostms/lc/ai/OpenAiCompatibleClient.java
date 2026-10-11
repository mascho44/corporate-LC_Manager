package de.ostms.lc.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Any server that speaks the common "chat completions" API: hosted providers (Mistral, OpenAI and others), vLLM, LM Studio, Ollama's /v1 endpoint, gateways.
 * Built by {@link LlmClientFactory} from the tenant configuration or the operator default (lc.ai.*). The key is only sent in the Authorization header and never logged.
 */
public class OpenAiCompatibleClient implements LlmClient {
 private static final ObjectMapper JSON=new ObjectMapper();
 private final URI endpoint;private final String model,key;private final Duration timeout;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 public OpenAiCompatibleClient(String baseUrl,String model,String apiKey,String apiKeyFile,int timeoutSeconds)throws IOException{
  if(baseUrl.isBlank()||model.isBlank())throw new IllegalStateException("lc.ai.base-url und lc.ai.model müssen für den Anbieter openai-compatible gesetzt sein.");
  var uri=URI.create(baseUrl.replaceAll("/+$","")+"/chat/completions");
  boolean local="localhost".equals(uri.getHost())||"127.0.0.1".equals(uri.getHost());
  if(!"https".equals(uri.getScheme())&&!local)throw new IllegalStateException("Externe KI-Anbieter müssen per HTTPS angebunden werden.");
  String k=apiKey!=null&&!apiKey.isBlank()?apiKey.strip():apiKeyFile!=null&&!apiKeyFile.isBlank()?Files.readString(Path.of(apiKeyFile)).strip():"";
  if(k.isEmpty()&&!local)throw new IllegalStateException("Für einen externen KI-Anbieter fehlt der API-Schlüssel (lc.ai.api-key oder lc.ai.api-key-file).");
  this.endpoint=uri;this.model=model;this.key=k;this.timeout=Duration.ofSeconds(Math.max(10,timeoutSeconds));
 }
 @Override public String provider(){return "openai-compatible";}
 @Override public String model(){return model;}
 @Override public String generateJson(String prompt)throws IOException{
  var body=JSON.writeValueAsString(Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),"response_format",Map.of("type","json_object"),"temperature",0));
  var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
  if(!key.isEmpty())request.header("Authorization","Bearer "+key);
  try{
   var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new IOException("KI-Dienst antwortet mit HTTP "+response.statusCode());
   return JSON.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("KI-Anfrage unterbrochen",e);}
 }
}
