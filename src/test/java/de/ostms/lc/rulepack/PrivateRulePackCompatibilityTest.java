package de.ostms.lc.rulepack;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.*;
/** Optional local check; no private fixture or fixed private pathname belongs in the repository. */
class PrivateRulePackCompatibilityTest {
 @Test void suppliedPrivatePackIsValidAndTestsPass()throws Exception{
  String path=System.getProperty("privatePackPath");assumeTrue(path!=null);
  var codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
  var pack=codec.parse(Files.readAllBytes(Path.of(path)));
  assertThat(PackEvaluator.test(pack).stream().filter(t->!t.passed()).count()).isZero();
 }
 @Test void suppliedPrivateSpecificationHasNoUnsupportedCapabilities()throws Exception{
  String path=System.getProperty("privateSpecPath");assumeTrue(path!=null);
  var codec=new PackCodec(new ObjectMapper().findAndRegisterModules());
  var report=(RuleSpecificationInspector.Report)codec.inspectSpecification(Files.readAllBytes(Path.of(path)));
  assertThat(report).isNotNull();assertThat(report.issues()).isEmpty();assertThat(report.importable()).isFalse();
 }
}
