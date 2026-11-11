package com.ai.fabric.platform.backend.mcp.connection;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class McpCredentialCryptoTest {
    @Test void envelopesUseFreshNoncesAndExactConnectionAndPurposeBinding() {
        var p = new McpConnectionProperties(); p.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var crypto = new McpCredentialCrypto(p);
        String a = crypto.encrypt("connection:a", "private-refresh-token");
        String b = crypto.encrypt("connection:a", "private-refresh-token");
        assertThat(a).isNotEqualTo(b).doesNotContain("private-refresh-token");
        assertThat(crypto.decrypt("connection:a", a)).isEqualTo("private-refresh-token");
        assertThatThrownBy(() -> crypto.decrypt("connection:b", a)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("attempt:a", a)).isInstanceOf(IllegalStateException.class);
    }
    @Test void keyRotationAcceptsOnlyExplicitOldVersionAndNeverInventsAKey() {
        var p = new McpConnectionProperties(); var crypto = new McpCredentialCrypto(p);
        assertThat(crypto.configured()).isFalse(); assertThatThrownBy(() -> crypto.encrypt("c", "token")).isInstanceOf(IllegalStateException.class);
        String oldKey = Base64.getEncoder().encodeToString(new byte[32]); p.setEncryptionKey(oldKey);
        String sealed = crypto.encrypt("c", "token");
        byte[] replacement = new byte[32]; replacement[0] = 7;
        p.setEncryptionKey(Base64.getEncoder().encodeToString(replacement)); p.setEncryptionKeyId("v2");
        assertThatThrownBy(() -> crypto.decrypt("c", sealed)).isInstanceOf(IllegalStateException.class);
        p.setPreviousEncryptionKey(oldKey); p.setPreviousEncryptionKeyId("v1");
        assertThat(crypto.decrypt("c", sealed)).isEqualTo("token");
        assertThat(crypto.encrypt("c", "token")).startsWith("v2.");
        assertThatThrownBy(() -> crypto.decrypt("c", sealed.substring(0, sealed.length() - 4) + "AAAA")).isInstanceOf(IllegalStateException.class);
    }
}
