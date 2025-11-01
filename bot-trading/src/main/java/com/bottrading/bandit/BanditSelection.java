package com.bottrading.bandit;

import java.util.Map;
import java.util.UUID;

public class BanditSelection {

  private final UUID armId;
  private final UUID presetId;
  private final BanditArmRole role;
  private final String decisionId;
  private final Map<String, Object> context;
  private final String presetKey;

  public BanditSelection(
      UUID armId,
      UUID presetId,
      BanditArmRole role,
      String decisionId,
      Map<String, Object> context,
      String presetKey) {
    this.armId = armId;
    this.presetId = presetId;
    this.role = role;
    this.decisionId = decisionId;
    this.context = context;
    this.presetKey = presetKey;
  }

  public UUID armId() {
    return armId;
  }

  public UUID presetId() {
    return presetId;
  }

  public BanditArmRole role() {
    return role;
  }

  public String decisionId() {
    return decisionId;
  }

  public Map<String, Object> context() {
    return context;
  }

  public String presetKey() {
    return presetKey;
  }
}
