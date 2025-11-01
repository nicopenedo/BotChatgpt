package com.bottrading.bandit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BanditContext {

  private final Map<String, Object> features;

  private BanditContext(Builder builder) {
    this.features = Collections.unmodifiableMap(new HashMap<>(builder.features));
  }

  public Map<String, Object> features() {
    return features;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Map<String, Object> features = new HashMap<>();

    public Builder put(String key, Object value) {
      if (value != null) {
        features.put(key, value);
      }
      return this;
    }

    public BanditContext build() {
      return new BanditContext(this);
    }
  }
}
