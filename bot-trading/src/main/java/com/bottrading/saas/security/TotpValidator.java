package com.bottrading.saas.security;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TotpValidator {

  private static final Logger log = LoggerFactory.getLogger(TotpValidator.class);
  private final TimeBasedOneTimePasswordGenerator generator;

  public TotpValidator() {
    this.generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30));
  }

  public boolean isValid(String secret, String code) {
    if (secret == null || code == null || code.isBlank()) {
      return false;
    }
    try {
      Base32 base32 = new Base32();
      byte[] keyBytes = base32.decode(secret.toUpperCase());
      var key = new SecretKeySpec(keyBytes, generator.getAlgorithm());
      Instant now = Instant.now();
      for (int i = -1; i <= 1; i++) {
        Instant offset = now.plus(Duration.ofSeconds(i * generator.getTimeStep().toSeconds()));
        int otp = generator.generateOneTimePassword(key, offset);
        String expected = format(otp);
        if (expected.equals(code.trim())) {
          return true;
        }
      }
    } catch (InvalidKeyException e) {
      log.warn("Invalid key for TOTP", e);
    }
    return false;
  }

  private String format(int otp) {
    return String.format("%0" + generator.getPasswordLength() + "d", otp);
  }
}
