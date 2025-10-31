package com.bottrading.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MathUtil {

  private MathUtil() {}

  public static BigDecimal floorToIncrement(BigDecimal value, BigDecimal increment) {
    if (value == null || increment == null) {
      throw new IllegalArgumentException("value and increment are required");
    }
    if (increment.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("increment must be positive");
    }
    BigDecimal divided = value.divide(increment, 16, RoundingMode.DOWN);
    return divided.multiply(increment).stripTrailingZeros();
  }

  public static BigDecimal min(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) < 0 ? a : b;
  }

  public static BigDecimal max(BigDecimal a, BigDecimal b) {
    return a.compareTo(b) > 0 ? a : b;
  }
}
