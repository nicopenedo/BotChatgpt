package com.bottrading.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandleSanitizerTest {

  private CandleSanitizer sanitizer;

  @BeforeEach
  void setUp() {
    sanitizer = new CandleSanitizer();
  }

  @Test
  void shouldFilterDuplicates() {
    List<Long> first = sanitizer.sanitize("BTC", "1m", 1_000L);
    List<Long> duplicate = sanitizer.sanitize("BTC", "1m", 1_000L);
    assertThat(first).containsExactly(1_000L);
    assertThat(duplicate).isEmpty();
  }

  @Test
  void shouldFillGaps() {
    sanitizer.sanitize("BTC", "1m", 1_000L);
    List<Long> result = sanitizer.sanitize("BTC", "1m", 1_000L + 3 * 60_000L);
    assertThat(result).containsExactly(1_000L + 60_000L, 1_000L + 120_000L, 1_000L + 180_000L);
  }
}
