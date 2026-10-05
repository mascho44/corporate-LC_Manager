package de.corporate.lc.imports.service;
import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class ExtractionConfidencePolicyTest {
 @Test void configurableThresholdAndBounds(){assertThat(new ExtractionConfidencePolicy(0.8).uncertain(0.45)).isTrue();assertThat(new ExtractionConfidencePolicy(0.8).uncertain(0.95)).isFalse();assertThat(new ExtractionConfidencePolicy(0.99).uncertain(0.95)).isTrue();assertThatThrownBy(()->new ExtractionConfidencePolicy(Double.NaN)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new ExtractionConfidencePolicy(1.1)).isInstanceOf(IllegalArgumentException.class);}
}
