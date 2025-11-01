package com.bottrading.model.enums;

public enum SnapshotWindow {
  DAYS_7("7d"),
  DAYS_30("30d"),
  DAYS_90("90d"),
  CUSTOM("custom");

  private final String label;

  SnapshotWindow(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public static SnapshotWindow fromLabel(String label) {
    for (SnapshotWindow window : values()) {
      if (window.label.equalsIgnoreCase(label)) {
        return window;
      }
    }
    return CUSTOM;
  }
}
