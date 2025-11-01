package com.bottrading.service.anomaly;

public interface AnomalyAlertPublisher {
  void publish(AnomalyNotification notification);
}
