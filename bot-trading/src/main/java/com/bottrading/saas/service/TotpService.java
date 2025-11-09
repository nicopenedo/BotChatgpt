package com.bottrading.saas.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

public interface TotpService {
  SecretKey generateSecret();
  String toBase32(SecretKey key);
  SecretKey fromBase32(String base32);
  boolean verify(SecretKey key, String code);
}

@Service
class TotpServiceImpl implements TotpService {

  private static final Duration STEP = Duration.ofSeconds(30);
  private static final int DIGITS = 6;
  private static final Base32 B32 = new Base32(false);
  private static final Pattern SANITIZE = Pattern.compile("[\\s\\-]");

  private final TimeBasedOneTimePasswordGenerator totp;

  TotpServiceImpl() {
    // En tu versión no lanza NoSuchAlgorithmException
    this.totp = new TimeBasedOneTimePasswordGenerator(STEP, DIGITS);
  }

  @Override
  public SecretKey generateSecret() {
    try {
      KeyGenerator kg = KeyGenerator.getInstance("HmacSHA1");
      return kg.generateKey();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot generate TOTP secret", e);
    }
  }

  @Override
  public String toBase32(SecretKey key) {
    if (key == null) return null;
    byte[] raw = key.getEncoded();
    String encoded = B32.encodeAsString(raw);
    return encoded.replace("=", "").toUpperCase(Locale.ROOT);
  }

  @Override
  public SecretKey fromBase32(String base32) {
    if (base32 == null || base32.isBlank()) return null;
    String normalized =
            SANITIZE.matcher(base32).replaceAll("")
                    .replace("_", "")
                    .replace("=", "")
                    .toUpperCase(Locale.ROOT);
    if (normalized.isEmpty()) return null;

    try {
      String padded = normalized;
      int remainder = normalized.length() % 8;
      if (remainder != 0) padded = normalized + "=".repeat(8 - remainder);
      byte[] decoded = B32.decode(padded);
      if (decoded != null && decoded.length > 0) {
        return new SecretKeySpec(decoded, "HmacSHA1");
      }
    } catch (IllegalArgumentException ignored) {
      // fallback a Base64 abajo
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(base32.trim());
      if (decoded.length > 0) {
        return new SecretKeySpec(decoded, "HmacSHA1");
      }
    } catch (IllegalArgumentException ignored) {
      // Not Base32 nor Base64
    }
    return null;
  }

  @Override
  public boolean verify(SecretKey key, String code) {
    if (key == null || code == null || code.isBlank()) return false;
    try {
      Instant now = Instant.now();
      int current  = totp.generateOneTimePassword(key, now);
      int previous = totp.generateOneTimePassword(key, now.minus(STEP));
      int next     = totp.generateOneTimePassword(key, now.plus(STEP));
      String given = code.trim();
      return given.equals(pad(current)) || given.equals(pad(previous)) || given.equals(pad(next));
    } catch (InvalidKeyException e) {
      return false;
    }
  }

  private static String pad(int value) {
    String digits = Integer.toString(value);
    if (digits.length() >= DIGITS) return digits;
    return "0".repeat(DIGITS - digits.length()) + digits;
  }
}
