package com.ai.fabric.platform.backend.assistant;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringCryptoTest {
    @Test void encryptsSourceContentWithRandomIvAndSessionBinding() {
        var properties = new AuthoringProperties(); properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var crypto = new AuthoringCrypto(properties);
        String a = crypto.encrypt("session-a", "Private API documentation");
        String b = crypto.encrypt("session-a", "Private API documentation");
        assertThat(a).isNotEqualTo(b).doesNotContain("Private API documentation");
        assertThat(crypto.decrypt("session-a", a)).isEqualTo("Private API documentation");
        assertThatThrownBy(() -> crypto.decrypt("session-b", a)).hasMessage("AUTHORING_CONTENT_DECRYPTION_FAILED");
    }
    @Test void doesNotInventAnEncryptionKeyOrAcceptTamperedCiphertext() {
        var properties = new AuthoringProperties(); var crypto = new AuthoringCrypto(properties);
        assertThat(crypto.configured()).isFalse();
        assertThatThrownBy(() -> crypto.encrypt("s", "text")).hasMessage("AUTHORING_CONTENT_ENCRYPTION_FAILED");
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        assertThatThrownBy(() -> crypto.decrypt("s", "invalid.ciphertext")).hasMessage("AUTHORING_CONTENT_DECRYPTION_FAILED");
    }
}
