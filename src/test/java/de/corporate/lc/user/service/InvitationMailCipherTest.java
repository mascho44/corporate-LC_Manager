package de.corporate.lc.user.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InvitationMailCipherTest {
 @Test void encryptionIsRandomizedAuthenticatedAndBoundToRow(){var cipher=new InvitationMailCipher("SyntheticEncryptionKeyOnlyAtLeast32Characters");String first=cipher.encrypt("row","synthetic-secret"),second=cipher.encrypt("row","synthetic-secret");assertThat(first).doesNotContain("synthetic-secret").isNotEqualTo(second);assertThat(cipher.decrypt("row",first)).isEqualTo("synthetic-secret");assertThatThrownBy(()->cipher.decrypt("other-row",first)).isInstanceOf(IllegalStateException.class);assertThatThrownBy(()->cipher.decrypt("row","invalid")).isInstanceOf(IllegalStateException.class);}
 @Test void missingEncryptionKeyFailsClosed(){assertThatThrownBy(()->new InvitationMailCipher("").encrypt("row","secret")).isInstanceOf(IllegalStateException.class);}
}
