package de.corporate.lc.check.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DeferredRulePacksTest {
 @Test void internalCatalogueDoesNotPublishIccPacksOrSourceAttribution() {
  assertThat(RuleCatalog.definitions()).hasSize(4);
  assertThat(RuleCatalog.definitions()).allMatch(r->r.sourceUrl()==null);
  assertThat(RuleCatalog.definitions()).noneMatch(r->r.id().startsWith("UCP"));
 }
 @Test void deferredEngineAndEndpointAreAbsent() {
  assertThatThrownBy(()->Class.forName("de.corporate.lc.check.service.RulePackEngine")).isInstanceOf(ClassNotFoundException.class);
  assertThatThrownBy(()->Class.forName("de.corporate.lc.check.api.RulePackController")).isInstanceOf(ClassNotFoundException.class);
 }
}
