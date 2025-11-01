package com.bottrading.config;

import com.bottrading.service.exchange.ExchangeReconciler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReconcileInitializer {

  private static final Logger log = LoggerFactory.getLogger(ReconcileInitializer.class);

  private final ExchangeReconciler exchangeReconciler;

  public ReconcileInitializer(ExchangeReconciler exchangeReconciler) {
    this.exchangeReconciler = exchangeReconciler;
  }

  @PostConstruct
  public void init() {
    if (exchangeReconciler.reconcileOnStartup()) {
      log.info("Running startup reconciliation");
      exchangeReconciler.reconcileAll();
    }
  }
}
