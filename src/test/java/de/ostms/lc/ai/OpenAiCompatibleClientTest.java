package de.ostms.lc.ai;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class OpenAiCompatibleClientTest {
 @Test void sendsTheKeyAndReadsTheMessageContentFromAnyCompatibleServer()throws Exception{
  var auth=new AtomicReference<String>();var body=new AtomicReference<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",ex->{
   auth.set(ex.getRequestHeaders().getFirst("Authorization"));body.set(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
   byte[] out="{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}".getBytes(StandardCharsets.UTF_8);
   ex.getResponseHeaders().add("Content-Type","application/json");ex.sendResponseHeaders(200,out.length);ex.getResponseBody().write(out);ex.close();});
  server.start();
  try{
   var client=new OpenAiCompatibleClient("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","some-model","secret-key","",30);
   assertThat(client.generateJson("hello")).isEqualTo("{\"ok\":true}");
   assertThat(auth.get()).isEqualTo("Bearer secret-key");
   assertThat(body.get()).contains("some-model").contains("json_object").contains("hello");
   assertThat(client.provider()).isEqualTo("openai-compatible");
  }finally{server.stop(0);}
 }
 @Test void externalServersNeedHttpsAndAKey(){
  assertThatThrownBy(()->new OpenAiCompatibleClient("http://example.org/v1","m","k","",30)).isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
  assertThatThrownBy(()->new OpenAiCompatibleClient("https://example.org/v1","m","","",30)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Schlüssel");
  assertThatThrownBy(()->new OpenAiCompatibleClient("","m","k","",30)).isInstanceOf(IllegalStateException.class);
 }
 @Test void aServerErrorBecomesAnIoExceptionWithoutLeakingTheKey()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",ex->{ex.sendResponseHeaders(401,-1);ex.close();});server.start();
  try{
   var client=new OpenAiCompatibleClient("http://127.0.0.1:"+server.getAddress().getPort()+"/v1","m","secret-key","",30);
   assertThatThrownBy(()->client.generateJson("x")).isInstanceOf(java.io.IOException.class).hasMessageContaining("401").hasMessageNotContaining("secret-key");
  }finally{server.stop(0);}
 }
}
