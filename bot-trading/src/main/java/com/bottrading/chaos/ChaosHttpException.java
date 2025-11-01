package com.bottrading.chaos;

public class ChaosHttpException extends RuntimeException {

  private final int httpStatusCode;

  public ChaosHttpException(int httpStatusCode, String message) {
    super(message);
    this.httpStatusCode = httpStatusCode;
  }

  public int getHttpStatusCode() {
    return httpStatusCode;
  }
}
