package com.bottrading.util;

import java.util.UUID;

public final class IdGenerator {

  private IdGenerator() {}

  public static String newClientOrderId() {
    return "scalp-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 24);
  }
}
