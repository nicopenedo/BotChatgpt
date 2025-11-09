package com.bottrading.saas.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class TotpServiceTest {

  private final TotpService service = new TotpServiceImpl();

  @Test
  void base32_roundtrip() throws Exception {
    SecretKey key = service.generateSecret();
    String encoded = service.toBase32(key);

    assertThat(encoded).isNotBlank();

    SecretKey decoded = service.fromBase32(encoded);
    assertThat(decoded).isNotNull();

    assertThat(service.toBase32(decoded)).isEqualTo(encoded);

    String currentCode = currentTotpCode(decoded);
    assertThat(service.verify(decoded, currentCode)).isTrue();
  }

  @Test
  void verifiesKnownBase32Secret() throws Exception {
    SecretKey key = service.fromBase32("JBSWY3DPEHPK3PXP");
    assertThat(key).isNotNull();

    String code = currentTotpCode(key);
    assertThat(service.verify(key, code)).isTrue();
  }

  @Test
  void accepts_legacy_base64() throws Exception {
    SecretKey key = service.generateSecret();
    String legacy = Base64.getEncoder().encodeToString(key.getEncoded());

    SecretKey decoded = service.fromBase32(legacy);
    assertThat(decoded).isNotNull();

    String currentCode = currentTotpCode(decoded);
    assertThat(service.verify(decoded, currentCode)).isTrue();
  }

  @Test
  void sanitizes_input() {
    SecretKey key = service.generateSecret();
    String encoded = service.toBase32(key);

    String messy =
        encoded.substring(0, 4)
            + " -"
            + encoded.substring(4).toLowerCase(Locale.ROOT)
            + "__";

    SecretKey decoded = service.fromBase32(messy);
    assertThat(decoded).isNotNull();
    assertThat(service.toBase32(decoded)).isEqualTo(encoded);
  }

  @Test
  void does_not_log_secrets() {
    boolean hasLoggerField =
        Arrays.stream(TotpServiceImpl.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getType)
            .anyMatch(Logger.class::isAssignableFrom);

    assertThat(hasLoggerField).isFalse();
  }

  private static String currentTotpCode(SecretKey key) throws Exception {
    TimeBasedOneTimePasswordGenerator generator =
        new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
    int value = generator.generateOneTimePassword(key, Instant.now());
    String digits = Integer.toString(value);
    if (digits.length() >= 6) {
      return digits;
    }
    return "0".repeat(6 - digits.length()) + digits;
  }
}
