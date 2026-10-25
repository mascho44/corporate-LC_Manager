package de.ostms.lc.rulepack;

import static de.ostms.lc.rulepack.PackDefinition.Field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class RuleWaiversTest {
    @Test void addsAndRemovesLcFields() {
        String one = RuleWaivers.update(null, Field.LC_SECOND_BENEFICIARY, true);
        String two = RuleWaivers.update(one, Field.LC_COVERAGE_FROM, true);
        assertThat(RuleWaivers.read(two)).containsExactlyInAnyOrder(Field.LC_SECOND_BENEFICIARY, Field.LC_COVERAGE_FROM);
        assertThat(RuleWaivers.read(RuleWaivers.update(two, Field.LC_SECOND_BENEFICIARY, false))).containsExactly(Field.LC_COVERAGE_FROM);
        assertThat(RuleWaivers.update(RuleWaivers.update(null, Field.LC_COVERAGE_FROM, true), Field.LC_COVERAGE_FROM, false)).isNull();
    }

    @Test void documentFieldsCannotBeWaived() {
        assertThatThrownBy(() -> RuleWaivers.update(null, Field.DOCUMENT_AMOUNT, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unreadableOrUnknownContentIsIgnored() {
        assertThat(RuleWaivers.read("kaputt")).isEmpty();
        assertThat(RuleWaivers.read("[\"GIBT_ES_NICHT\"]")).isEmpty();
        assertThat(RuleWaivers.read(null)).isEmpty();
    }
}
