package com.bottrading.util;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MathUtilTest {

  @Test
  void floorToIncrementRoundsDown() {
    BigDecimal result = MathUtil.floorToIncrement(new BigDecimal("1.2345"), new BigDecimal("0.01"));
    Assertions.assertEquals(new BigDecimal("1.23"), result);
  }

  @Test
  void floorToIncrementThrowsWhenIncrementZero() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> MathUtil.floorToIncrement(BigDecimal.ONE, BigDecimal.ZERO));
  }
}
