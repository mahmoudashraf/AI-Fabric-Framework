package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class ExternalIntegrationIntegrity {
    private final CustomerOfferCodec codec;
    private final PlatformSecretService secrets;
    public ExternalIntegrationIntegrity(CustomerOfferCodec codec, PlatformSecretService secrets) {
        this.codec = codec; this.secrets = secrets;
    }
    public String seal(Object evidence) {
        String key = secrets.resolveSecret("PLATFORM_ARTIFACT_SIGNING_KEY");
        if (key == null || key.isBlank()) throw ExternalIntegrationPolicy.conflict("INTEGRITY_KEY_REQUIRED");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(codec.hash(evidence).getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("External integration integrity unavailable", error); }
    }
    public void require(Object evidence, String expected) {
        if (expected == null || !MessageDigest.isEqual(seal(evidence).getBytes(StandardCharsets.US_ASCII),
            expected.getBytes(StandardCharsets.US_ASCII))) throw ExternalIntegrationPolicy.conflict("INTEGRITY_INVALID");
    }
}
