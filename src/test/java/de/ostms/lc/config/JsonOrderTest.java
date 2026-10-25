package de.ostms.lc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class JsonOrderTest {
 record Sample(String zeta,String alpha){}

 @Test void applicationYmlKeepsTheDeclarationOrderOfPropertiesInJson() throws Exception {
  var source=new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")).get(0);
  new ApplicationContextRunner()
   .withInitializer(ctx->ctx.getEnvironment().getPropertySources().addFirst(source))
   .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
   .run(ctx->assertEquals("{\"zeta\":\"1\",\"alpha\":\"2\"}",ctx.getBean(JsonMapper.class).writeValueAsString(new Sample("1","2"))));
 }
}
