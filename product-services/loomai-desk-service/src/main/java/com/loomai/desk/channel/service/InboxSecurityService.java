package com.loomai.desk.channel.service;

import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.model.InboxContracts.OperatorIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Reuses Basic Inbox encryption/signing, without process-global operator credentials. */
public class InboxSecurityService {
  private final BasicInboxProperties properties;
  private final SecureRandom random = new SecureRandom();

  public InboxSecurityService(BasicInboxProperties properties) {
    this.properties = properties;
  }

  public void requireBinding(OperatorIdentity operator, String bindingId) {
    if (operator == null || !operator.bindingIds().contains(bindingId)) {
      throw new InboxSecurityException(
          "OPERATOR_BINDING_DENIED", "Operator is not assigned to this binding.");
    }
  }

  public String encrypt(String value) {
    if (value == null) return null;
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
    } catch (Exception e) {
      throw new IllegalStateException("Desk encryption failed.", e);
    }
  }

  public String decrypt(String value) {
    if (value == null) return null;
    try {
      ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(value));
      byte[] iv = new byte[12];
      payload.get(iv);
      byte[] encrypted = new byte[payload.remaining()];
      payload.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Desk decryption failed.", e);
    }
  }

  public String sign(String timestamp, String nonce, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.getCallbackSigningSecret().getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal((timestamp + "." + nonce + "." + body).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Desk callback signing failed.", e);
    }
  }

  public String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private SecretKeySpec encryptionKey() throws Exception {
    return new SecretKeySpec(
        MessageDigest.getInstance("SHA-256")
            .digest(properties.getDataEncryptionSecret().getBytes(StandardCharsets.UTF_8)),
        "AES");
  }

  public static boolean constantTime(String expected, String actual) {
    return expected != null
        && actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }

  public static final class InboxSecurityException extends RuntimeException {
    private final String code;

    public InboxSecurityException(String code, String message) {
      super(message);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }
}
