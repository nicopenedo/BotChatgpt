package com.bottrading.strategy;

import com.bottrading.bandit.BanditSelection;
import com.bottrading.research.regime.Regime;

public record StrategyDecision(
    SignalResult signal,
    StrategyContext context,
    Regime regime,
    String preset,
    BanditSelection banditSelection) {}
