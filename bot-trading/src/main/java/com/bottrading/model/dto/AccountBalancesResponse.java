package com.bottrading.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountBalancesResponse(List<Balance> balances) {

  public record Balance(String asset, BigDecimal free, BigDecimal locked) {}
}
