package com.bottrading.research.nightly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResearchNightlyScheduler {

  private static final Logger log = LoggerFactory.getLogger(ResearchNightlyScheduler.class);

  private final ResearchProperties properties;
  private final ResearchNightlyPipeline pipeline;

  public ResearchNightlyScheduler(ResearchProperties properties, ResearchNightlyPipeline pipeline) {
    this.properties = properties;
    this.pipeline = pipeline;
  }

  @Scheduled(
      cron = "${research.nightly.start-cron:0 20 * * *}",
      zone = "${research.nightly.zone:UTC}")
  public void triggerNightly() {
    ResearchProperties.Nightly nightly = properties.getNightly();
    if (nightly == null || !nightly.isEnabled()) {
      log.debug("Skipping nightly research schedule (disabled)");
      return;
    }
    pipeline.runNightly();
  }
}
