package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentBundleSealingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeploymentBundleSealingService service = new DeploymentBundleSealingService(objectMapper);

    @Test
    void sealsAndUnsealsSecretPayloadWithoutPlaintextInEnvelope() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("secret", "runtime-secret-value");

        DeploymentBundleSealingService.SealedPayload sealed = service.seal(
            payload,
            publicKeyPem(keyPair),
            Map.of("bundleId", "dxb-test")
        );

        assertThat(sealed.envelope().toString()).doesNotContain("runtime-secret-value");
        assertThat(sealed.envelopeHash()).startsWith("sha256:");

        assertThat(service.unseal(sealed.envelope(), privateKeyPem(keyPair)).path("secret").asText())
            .isEqualTo("runtime-secret-value");
    }

    @Test
    void failsClosedWhenPrivateKeyDoesNotMatch() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        KeyPair otherKeyPair = rsaKeyPair();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("secret", "runtime-secret-value");

        DeploymentBundleSealingService.SealedPayload sealed = service.seal(
            payload,
            publicKeyPem(keyPair),
            Map.of("bundleId", "dxb-test")
        );

        assertThatThrownBy(() -> service.unseal(sealed.envelope(), privateKeyPem(otherKeyPair)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Sealed bundle secrets could not be decrypted");
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicKeyPem(KeyPair keyPair) {
        return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String privateKeyPem(KeyPair keyPair) {
        return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    private static String pem(String label, byte[] key) {
        return "-----BEGIN " + label + "-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key)
            + "\n-----END " + label + "-----\n";
    }
}
