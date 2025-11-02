package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecretEncryptionService {

  private static final Logger log = LoggerFactory.getLogger(SecretEncryptionService.class);
  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH = 128;

  private final SaasProperties properties;
  private final SecureRandom secureRandom = new SecureRandom();
  private SecretKey key;

  public SecretEncryptionService(SaasProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void init() {
    String kmsKey = properties.getSecurity().getKmsMasterKey();
    if (kmsKey == null || kmsKey.isBlank()) {
      log.warn("No KMS master key configured, generating ephemeral key for development use only");
      byte[] keyBytes = new byte[32];
      secureRandom.nextBytes(keyBytes);
      this.key = new SecretKeySpec(keyBytes, "AES");
    } else {
      byte[] decoded = Base64.getDecoder().decode(kmsKey);
      if (decoded.length != 32) {
        throw new IllegalArgumentException("KMS master key must be 32 bytes base64");
      }
      this.key = new SecretKeySpec(decoded, "AES");
    }
  }

  public byte[] encrypt(String secret) {
    if (secret == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] ciphertext = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      buffer.put(iv);
      buffer.put(ciphertext);
      return buffer.array();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to encrypt secret", e);
    }
  }

  public String decrypt(byte[] payload) {
    if (payload == null) {
      return null;
    }
    try {
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      byte[] iv = new byte[IV_LENGTH];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
      byte[] plain = cipher.doFinal(ciphertext);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to decrypt secret", e);
    }
  }
}
