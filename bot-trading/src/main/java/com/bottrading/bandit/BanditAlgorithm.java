package com.bottrading.bandit;

import java.util.List;

public interface BanditAlgorithm {
  BanditArmEntity choose(List<BanditArmEntity> arms, BanditContext context);

  String name();
}
