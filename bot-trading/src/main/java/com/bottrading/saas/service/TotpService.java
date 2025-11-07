package com.bottrading.saas.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

public interface TotpService {
  SecretKey generateSecret();

  String toBase32(SecretKey key);

  SecretKey fromBase32(String base32);

  boolean verify(SecretKey key, String code);
}

@Service
class TotpServiceImpl implements TotpService {

  private final TimeBasedOneTimePasswordGenerator totp;

  TotpServiceImpl() {
    try {
      this.totp = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public SecretKey generateSecret() {
    try {
      KeyGenerator kg = KeyGenerator.getInstance("HmacSHA1");
      return kg.generateKey();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public String toBase32(SecretKey key) {
    if (key == null) {
      return null;
    }
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }

  @Override
  public SecretKey fromBase32(String base32) {
    if (base32 == null || base32.isBlank()) {
      return null;
    }
    try {
      byte[] bytes = Base64.getDecoder().decode(base32);
      return new SecretKeySpec(bytes, "HmacSHA1");
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @Override
  public boolean verify(SecretKey key, String code) {
    if (key == null || code == null) {
      return false;
    }
    try {
      Instant now = Instant.now();
      int current = totp.generateOneTimePassword(key, now);
      int previous = totp.generateOneTimePassword(key, now.minus(Duration.ofSeconds(30)));
      int next = totp.generateOneTimePassword(key, now.plus(Duration.ofSeconds(30)));
      String formatted = String.format("%06d", current);
      if (formatted.equals(code)) {
        return true;
      }
      if (String.format("%06d", previous).equals(code)) {
        return true;
      }
      return String.format("%06d", next).equals(code);
    } catch (InvalidKeyException e) {
      return false;
    }
  }
}
