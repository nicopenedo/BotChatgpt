package com.bottrading.throttle;

public enum Endpoint {
  PRICE_TICKER(1, 1),
  KLINES(1, 1),
  TICKER_24H(1, 1),
  EXCHANGE_INFO(10, 10),
  ACCOUNT_INFORMATION(10, 10),
  COMMISSION(5, 5),
  NEW_ORDER(1, 1),
  ORDER_STATUS(1, 1),
  CANCEL_ORDER(1, 1),
  USER_STREAM_START(1, 1),
  USER_STREAM_KEEP_ALIVE(1, 1),
  USER_STREAM_CLOSE(1, 1),
  GENERIC(1, 1);

  private final int weight1s;
  private final int weight60s;

  Endpoint(int weight1s, int weight60s) {
    this.weight1s = weight1s;
    this.weight60s = weight60s;
  }

  public int getWeight1s() {
    return weight1s;
  }

  public int getWeight60s() {
    return weight60s;
  }
}
